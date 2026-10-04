package cn.openlims.platform.points.service;

import cn.openlims.platform.achievement.model.CompetitionEntity;
import cn.openlims.platform.achievement.model.CompetitionLifecycle;
import cn.openlims.platform.achievement.model.VerificationStatus;
import cn.openlims.platform.achievement.repository.CompetitionRepository;
import cn.openlims.platform.common.error.ApiException;
import cn.openlims.platform.identity.model.AccountEntity;
import cn.openlims.platform.identity.model.MemberProfileEntity;
import cn.openlims.platform.identity.model.MemberStatus;
import cn.openlims.platform.identity.model.Role;
import cn.openlims.platform.identity.repository.MemberProfileRepository;
import cn.openlims.platform.identity.service.AuthService;
import cn.openlims.platform.notification.service.NotificationService;
import cn.openlims.platform.points.api.PointModels;
import cn.openlims.platform.points.model.PointCategory;
import cn.openlims.platform.points.model.PointEntryEntity;
import cn.openlims.platform.points.model.PointGrantEntity;
import cn.openlims.platform.points.model.PointGrantType;
import cn.openlims.platform.points.model.PointSubcategory;
import cn.openlims.platform.points.repository.PointEntryRepository;
import cn.openlims.platform.points.repository.PointGrantRepository;
import cn.openlims.platform.project.model.ProjectTeamEntity;
import cn.openlims.platform.project.repository.ProjectTeamRepository;
import cn.openlims.platform.publicsite.model.PublicShowcase;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PointService {

    private static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int HEATMAP_DAYS = 365;

    private final PointGrantRepository grants;
    private final PointEntryRepository entries;
    private final MemberProfileRepository profiles;
    private final AuthService authService;
    private final NotificationService notifications;
    private final CompetitionRepository competitions;
    private final ProjectTeamRepository projects;
    private final TransactionTemplate transactions;

    public PointService(
            PointGrantRepository grants,
            PointEntryRepository entries,
            MemberProfileRepository profiles,
            AuthService authService,
            NotificationService notifications,
            CompetitionRepository competitions,
            ProjectTeamRepository projects,
            PlatformTransactionManager transactionManager
    ) {
        this.grants = grants;
        this.entries = entries;
        this.profiles = profiles;
        this.authService = authService;
        this.notifications = notifications;
        this.competitions = competitions;
        this.projects = projects;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @PreAuthorize("hasAuthority('POINTS_MANAGE')")
    public PointModels.GrantView grant(Authentication authentication, PointModels.ManualGrantRequest request) {
        AccountEntity operator = authService.requireAccount(authentication);
        String fingerprint = fingerprint(request);
        try {
            return transactions.execute(status -> manualGrant(operator, request, fingerprint));
        } catch (DataIntegrityViolationException conflict) {
            // The failed transaction has ended; resolve concurrent retries in a fresh transaction.
            return transactions.execute(status -> {
                PointGrantEntity existing = grants.findByRequestKey(request.requestKey()).orElse(null);
                if (existing == null) throw conflict;
                return replay(existing, operator, fingerprint);
            });
        }
    }

    private PointModels.GrantView replay(PointGrantEntity existing, AccountEntity operator, String fingerprint) {
        if (!existing.getOperator().getId().equals(operator.getId())
                || !fingerprint.equals(existing.getRequestFingerprint())) {
            throw new ApiException(HttpStatus.CONFLICT, "该提交编号已用于其他积分事项，请刷新后重新提交");
        }
        return toGrantView(existing);
    }

    private PointModels.GrantView manualGrant(AccountEntity operator, PointModels.ManualGrantRequest request,
                                             String fingerprint) {
        PointGrantEntity existing = grants.findByRequestKey(request.requestKey()).orElse(null);
        if (existing != null) return replay(existing, operator, fingerprint);
        CompetitionEntity competition = null;
        ProjectTeamEntity project = null;
        Set<UUID> allowed = new HashSet<>();
        if (request.subcategory() == PointSubcategory.COMPETITION_AWARD) {
            if (request.competitionId() == null || request.projectId() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "竞赛积分必须选择竞赛库中的已审核获奖记录");
            }
            competition = competitions.findByIdForUpdate(request.competitionId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "关联竞赛不存在"));
            if (!eligibleCompetition(competition)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "只能为已结束、审核通过且有奖项的竞赛发放积分");
            }
            competitionMembers(competition).forEach(member -> allowed.add(member.getId()));
        } else if (request.subcategory() == PointSubcategory.PROJECT_TASK) {
            if (request.projectId() == null || request.competitionId() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "项目积分必须选择项目库中的项目");
            }
            project = projects.findByIdForUpdate(request.projectId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "关联项目不存在"));
            projectMembers(project).forEach(member -> allowed.add(member.getId()));
        } else if (request.projectId() != null || request.competitionId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该积分类型不接受项目或竞赛关联");
        }
        if (competition != null || project != null) {
            for (var allocation : request.allocations()) {
                if (!allowed.contains(allocation.memberProfileId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "只能给所选项目或竞赛的关联成员发放积分，请刷新成员名单");
                }
            }
        }
        var internal = new PointModels.GrantRequest(request.title(), request.subcategory(), request.occurredOn(),
                request.itemTotalPoints(), nextNumber(), "", request.description(), request.allocations());
        var result = grantAs(operator, internal);
        PointGrantEntity saved = grants.findById(result.id()).orElseThrow();
        saved.recordManualSource(competition, project, request.requestKey(), fingerprint);
        grants.saveAndFlush(saved);
        return toGrantView(saved);
    }

    @PreAuthorize("hasAuthority('POINTS_MANAGE')")
    @Transactional(readOnly = true)
    public PointModels.SourceOptions sources(Authentication authentication) {
        AccountEntity operator = authService.requireAccount(authentication);
        return new PointModels.SourceOptions(
                competitions.findAll().stream().filter(PointService::eligibleCompetition)
                        .sorted(Comparator.comparing(CompetitionEntity::getName))
                        .map(source -> new PointModels.SourceOption(source.getId(), source.getName(),
                                source.getAwardName(), sourceMembers(operator, competitionMembers(source)))).toList(),
                projects.findAll().stream().sorted(Comparator.comparing(ProjectTeamEntity::getProjectName))
                        .map(source -> new PointModels.SourceOption(source.getId(), source.getProjectName(), null,
                                sourceMembers(operator, projectMembers(source)))).toList());
    }

    private List<PointModels.SourceMember> sourceMembers(AccountEntity operator, List<MemberProfileEntity> members) {
        return members.stream().filter(member -> taskRecipientIneligibleReason(operator, member) == null)
                .collect(java.util.stream.Collectors.toMap(MemberProfileEntity::getId, member -> member, (a, b) -> a))
                .values().stream().sorted(Comparator.comparing(MemberProfileEntity::getName))
                .map(member -> new PointModels.SourceMember(member.getId(), member.getName(),
                        member.getMemberCode(), member.getTotalPoints())).toList();
    }

    private static List<MemberProfileEntity> competitionMembers(CompetitionEntity source) {
        var members = new ArrayList<MemberProfileEntity>();
        members.add(source.getCaptainProfile());
        source.getParticipants().forEach(item -> { if (item.getLinkedProfile() != null) members.add(item.getLinkedProfile()); });
        return members;
    }

    private static List<MemberProfileEntity> projectMembers(ProjectTeamEntity source) {
        var members = new ArrayList<>(source.getMembers());
        members.add(source.getLeader());
        return members;
    }

    private static boolean eligibleCompetition(CompetitionEntity source) {
        return source.isAwardedResult()
                && source.getVerificationStatus() == VerificationStatus.APPROVED
                && normalize(source.getAwardName()) != null;
    }

    private static String nextNumber() {
        return "PTS-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(LAB_TIME_ZONE)
                .format(Instant.now()) + "-" + UUID.randomUUID();
    }

    private static String fingerprint(PointModels.ManualGrantRequest request) {
        StringBuilder value = new StringBuilder();
        java.util.function.Consumer<Object> add = item -> {
            String text = item == null ? "" : item.toString().trim();
            value.append(text.length()).append(':').append(text);
        };
        add.accept(request.title()); add.accept(request.subcategory()); add.accept(request.occurredOn());
        add.accept(request.itemTotalPoints()); add.accept(request.competitionId()); add.accept(request.projectId());
        add.accept(request.description());
        request.allocations().stream().sorted(Comparator.comparing(PointModels.AllocationRequest::memberProfileId))
                .forEach(item -> { add.accept(item.memberProfileId()); add.accept(item.points()); add.accept(item.contribution()); });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * 发放的核心实现。
     *
     * <p>拆出这一层是为了让「有认证上下文的公开发放」与「任务结算的系统发放」复用同一段校验与落库逻辑：
     * 公开路径 {@link #grant} 负责把认证换成操作人并保留 {@code @PreAuthorize}；
     * 结算路径 {@link #grantForTaskSettlement} 的操作人由任务模块显式传入（任务创建者）。
     * 两条路径都会走 {@link #validateAllocations} 与 {@link #validateRecipient}，因此规则完全一致。</p>
     */
    private PointModels.GrantView grantAs(AccountEntity operator, PointModels.GrantRequest request) {
        String sourceReference = request.sourceReference().trim();
        if (grants.existsBySourceReferenceIgnoreCase(sourceReference)) {
            throw new ApiException(HttpStatus.CONFLICT, "该积分来源已经发放，请勿重复提交");
        }

        validateAllocations(request);
        List<PointModels.AllocationRequest> sortedAllocations = request.allocations().stream()
                .sorted(Comparator.comparing(PointModels.AllocationRequest::memberProfileId))
                .toList();
        List<LockedAllocation> locked = new ArrayList<>();
        for (PointModels.AllocationRequest allocation : sortedAllocations) {
            MemberProfileEntity member = profiles.findByIdForUpdate(allocation.memberProfileId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "积分接收成员不存在"));
            validateRecipient(operator, member);
            int creditedPoints = creditedPoints(
                    member,
                    request.subcategory(),
                    request.occurredOn(),
                    allocation.points()
            );
            locked.add(new LockedAllocation(
                    member,
                    allocation.points(),
                    creditedPoints,
                    allocation.contribution().trim()
            ));
        }

        PointGrantEntity grant = new PointGrantEntity(
                PointGrantType.GRANT,
                request.subcategory(),
                request.title().trim(),
                request.occurredOn(),
                request.itemTotalPoints(),
                sourceReference,
                request.evidenceUrl(),
                normalize(request.description()),
                operator,
                null
        );
        for (LockedAllocation allocation : locked) {
            allocation.member().applyPointDelta(allocation.creditedPoints());
            grant.addEntry(new PointEntryEntity(
                    grant,
                    allocation.member(),
                    allocation.requestedPoints(),
                    allocation.creditedPoints(),
                    allocation.contribution()
            ));
        }
        PointGrantEntity saved = grants.save(grant);
        profiles.saveAll(locked.stream().map(LockedAllocation::member).toList());
        for (LockedAllocation allocation : locked) {
            notifications.send(
                    allocation.member().getAccount(),
                    "POINTS_GRANTED",
                    "积分已到账",
                    pointNotificationSummary(request.title().trim(), allocation),
                    "/profile"
            );
        }
        return toGrantView(saved);
    }

    /**
     * 任务到期结算时给单个对象发放积分。
     *
     * <p>调用方是任务模块的结算服务（定时任务或「结束任务」触发），<b>没有认证上下文</b>，因此
     * 操作人由调用方显式传入，约定为**任务创建者**。这里刻意不加 {@code @PreAuthorize}
     * （它只被内部调用、也不暴露任何 Controller），并且仍然走 {@link #validateRecipient}
     * 与 {@link #validateAllocations}，所以并没有放宽积分模块的任何既有规则。</p>
     *
     * <p>幂等由来源编号唯一约束保证：{@code sourcePrefix:taskId:memberProfileId}
     * （普通任务沿用历史上的 {@code TASK:} 前缀，悬赏用 {@code BOUNTY:}）。重复结算直接复用原批次，
     * 既不重复计分也不报错——这也是「延长截止日期后重新结算」能够安全重跑的前提。</p>
     *
     * @param sourcePrefix 来源编号前缀，用于区分任务类型并保持与历史记录一致
     */
    @Transactional
    public TaskGrantResult grantForTaskSettlement(
            UUID taskId,
            UUID memberProfileId,
            int points,
            AccountEntity operator,
            String sourcePrefix,
            String taskTitle,
            LocalDate occurredOn,
            String evidenceUrl,
            String description,
            String contribution
    ) {
        String sourceReference = sourcePrefix + ":" + taskId + ":" + memberProfileId;
        // 幂等：来源编号唯一，重复结算直接复用原批次，不重复计分也不报错。
        PointGrantEntity existing = grants.findBySourceReferenceIgnoreCase(sourceReference).orElse(null);
        if (existing != null) {
            int credited = existing.getEntries().stream().mapToInt(PointEntryEntity::getPoints).sum();
            return new TaskGrantResult(true, existing.getId(), credited, null);
        }
        if (points <= 0) {
            return new TaskGrantResult(false, null, null, null);
        }
        MemberProfileEntity member = profiles.findById(memberProfileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "积分接收成员不存在"));
        String ineligible = taskRecipientIneligibleReason(operator, member);
        if (ineligible != null) {
            return new TaskGrantResult(false, null, null, ineligible);
        }
        PointModels.GrantRequest request = new PointModels.GrantRequest(
                limit("任务：" + taskTitle, 160),
                PointSubcategory.PROJECT_TASK,
                occurredOn,
                points,
                sourceReference,
                validateEvidenceUrl(evidenceUrl),
                normalize(description),
                List.of(new PointModels.AllocationRequest(
                        memberProfileId, points, limit(contribution, 500)))
        );
        PointModels.GrantView granted = grantAs(operator, request);
        return new TaskGrantResult(true, granted.id(), granted.awardedPoints(), null);
    }

    /**
     * 任务积分的接收方是否不可计分。返回 null 表示可以计分，否则返回不可计分的原因。
     * 与 {@link #validateRecipient} 保持一致，但按「跳过并记录原因」而不是抛错的方式返回。
     */
    private String taskRecipientIneligibleReason(AccountEntity operator, MemberProfileEntity member) {
        if (member.getAccount().getRole() == Role.TEACHER) return "指导教师不参与成员积分统计";
        if (member.getStatus() != MemberStatus.OFFICIAL) return "只能给正式成员发放积分";
        if (member.getAccount().getId().equals(operator.getId())) return "积分管理员不能给自己发放积分";
        return null;
    }

    @PreAuthorize("hasAuthority('POINTS_MANAGE')")
    @Transactional
    public PointModels.GrantView reverse(
            Authentication authentication,
            UUID grantId,
            PointModels.ReversalRequest request
    ) {
        AccountEntity operator = authService.requireAccount(authentication);
        PointGrantEntity original = grants.findById(grantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "积分发放记录不存在"));
        if (original.getType() == PointGrantType.REVERSAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "撤销记录不能再次撤销");
        }
        if (grants.existsByReversalOf_Id(grantId)) {
            throw new ApiException(HttpStatus.CONFLICT, "该积分发放已经撤销");
        }

        List<PointEntryEntity> originalEntries = original.getEntries().stream()
                .sorted(Comparator.comparing(entry -> entry.getMember().getId()))
                .toList();
        Map<UUID, MemberProfileEntity> lockedMembers = new java.util.LinkedHashMap<>();
        for (PointEntryEntity originalEntry : originalEntries) {
            MemberProfileEntity member = profiles.findByIdForUpdate(originalEntry.getMember().getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "积分接收成员不存在"));
            lockedMembers.put(member.getId(), member);
        }

        PointGrantEntity reversal = new PointGrantEntity(
                PointGrantType.REVERSAL,
                original.getSubcategory(),
                limit("撤销：" + original.getTitle(), 160),
                original.getOccurredOn(),
                -original.getItemTotalPoints(),
                nextNumber(),
                "",
                request.reason().trim(),
                operator,
                original
        );
        reversal.copySource(original);
        for (PointEntryEntity originalEntry : originalEntries) {
            MemberProfileEntity member = lockedMembers.get(originalEntry.getMember().getId());
            int delta = -originalEntry.getPoints();
            member.applyPointDelta(delta);
            reversal.addEntry(new PointEntryEntity(
                    reversal,
                    member,
                    -originalEntry.getRequestedPoints(),
                    delta,
                    originalEntry.getContribution()
            ));
        }
        PointGrantEntity saved = grants.save(reversal);
        profiles.saveAll(lockedMembers.values());
        for (PointEntryEntity originalEntry : originalEntries) {
            MemberProfileEntity member = lockedMembers.get(originalEntry.getMember().getId());
            notifications.send(
                    member.getAccount(),
                    "POINTS_REVERSED",
                    "积分已调整",
                    original.getTitle() + "：-" + originalEntry.getPoints() + " 分。" + request.reason().trim(),
                    "/profile"
            );
        }
        return toGrantView(saved);
    }

    @PreAuthorize("hasAuthority('POINTS_MANAGE')")
    @Transactional(readOnly = true)
    public List<PointModels.GrantView> recentGrants() {
        return grants.findTop200ByOrderByCreatedAtDesc().stream().map(this::toGrantView).toList();
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public PointModels.MemberSummary ownSummary(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        MemberProfileEntity profile = profiles.findByAccountId(account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "当前账号还没有成员资料"));
        List<PointEntryEntity> allEntries = entries.findByMember_Id(profile.getId());
        Map<PointCategory, Integer> categoryTotals = new EnumMap<>(PointCategory.class);
        Map<PointSubcategory, Integer> subcategoryTotals = new EnumMap<>(PointSubcategory.class);
        for (PointCategory category : PointCategory.values()) categoryTotals.put(category, 0);
        for (PointSubcategory subcategory : PointSubcategory.values()) subcategoryTotals.put(subcategory, 0);
        for (PointEntryEntity entry : allEntries) {
            PointSubcategory subcategory = entry.getGrant().getSubcategory();
            categoryTotals.merge(subcategory.category(), entry.getPoints(), Math::addExact);
            subcategoryTotals.merge(subcategory, entry.getPoints(), Math::addExact);
        }
        List<PointModels.EntryView> recent = entries
                .findTop200ByMember_IdOrderByGrant_OccurredOnDescCreatedAtDesc(profile.getId())
                .stream().map(this::toEntryView).toList();
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        List<PointModels.DailyPointView> dailyPoints = entries
                .sumDailyForMember(profile.getId(), today.minusDays(HEATMAP_DAYS - 1L), today.plusDays(1))
                .stream()
                .map(total -> new PointModels.DailyPointView(total.getDate(), Math.toIntExact(total.getPoints())))
                .toList();
        List<MemberProfileEntity> participants = rankingParticipants();
        Integer totalRank = profile.getAccount().getRole() == Role.TEACHER
                || profile.getStatus() != MemberStatus.OFFICIAL
                ? null
                : 1 + (int) participants.stream()
                        .filter(item -> item.getTotalPoints() > profile.getTotalPoints())
                        .count();
        return new PointModels.MemberSummary(
                profile.getTotalPoints(),
                totalRank,
                participants.size(),
                categoryTotals,
                subcategoryTotals,
                recent,
                dailyPoints
        );
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public PointModels.LeaderboardView leaderboard(
            Authentication authentication,
            PointModels.LeaderboardPeriod period
    ) {
        AccountEntity account = authService.requireAccount(authentication);
        UUID currentProfileId = profiles.findByAccountId(account.getId()).map(MemberProfileEntity::getId).orElse(null);
        DateRange range = dateRange(period, LocalDate.now(LAB_TIME_ZONE));
        List<RankedMember> ranked = rankedMembers(period);

        List<PointModels.LeaderboardEntry> result = new ArrayList<>();
        int previousPoints = Integer.MIN_VALUE;
        int currentRank = 0;
        for (int index = 0; index < ranked.size(); index++) {
            RankedMember item = ranked.get(index);
            if (item.points() != previousPoints) currentRank = index + 1;
            previousPoints = item.points();
            MemberProfileEntity profile = item.profile();
            result.add(new PointModels.LeaderboardEntry(
                    currentRank,
                    profile.getId(),
                    profile.getName(),
                    profile.getMemberCode(),
                    profile.getAvatarUrl(),
                    profile.getAccount().getRole(),
                    item.points(),
                    profile.getTotalPoints(),
                    profile.getId().equals(currentProfileId)
            ));
        }

        return new PointModels.LeaderboardView(
                period,
                period == PointModels.LeaderboardPeriod.TOTAL ? null : range.startInclusive(),
                period == PointModels.LeaderboardPeriod.TOTAL ? null : range.endExclusive().minusDays(1),
                Instant.now(),
                result
        );
    }

    private List<RankedMember> rankedMembers(PointModels.LeaderboardPeriod period) {
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        DateRange range = dateRange(period, today);
        Map<UUID, Integer> periodPoints = new HashMap<>();
        if (period != PointModels.LeaderboardPeriod.TOTAL) {
            for (PointEntryRepository.MemberPointTotal total
                    : entries.sumByMemberForPeriod(range.startInclusive(), range.endExclusive())) {
                periodPoints.put(total.getMemberId(), Math.toIntExact(total.getPoints()));
            }
        }

        return rankingParticipants().stream()
                .map(profile -> new RankedMember(
                        profile,
                        period == PointModels.LeaderboardPeriod.TOTAL
                                ? profile.getTotalPoints()
                                : periodPoints.getOrDefault(profile.getId(), 0)
                ))
                .sorted(Comparator.comparingInt(RankedMember::points).reversed()
                        .thenComparing(Comparator.comparingInt(
                                (RankedMember item) -> item.profile().getTotalPoints()).reversed())
                        .thenComparing(item -> item.profile().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(item -> item.profile().getId()))
                .toList();

    }

    @Transactional(readOnly = true)
    public Map<String, List<PublicShowcase.RankingEntry>> publicRankings() {
        Map<String, List<PublicShowcase.RankingEntry>> boards = new LinkedHashMap<>();
        for (var period : List.of(PointModels.LeaderboardPeriod.TOTAL, PointModels.LeaderboardPeriod.MONTH,
                PointModels.LeaderboardPeriod.YEAR)) {
            String name = period == PointModels.LeaderboardPeriod.TOTAL ? "总榜"
                    : period == PointModels.LeaderboardPeriod.MONTH ? "月榜" : "年榜";
            var rows = new ArrayList<PublicShowcase.RankingEntry>();
            int previous = Integer.MIN_VALUE;
            int rank = 0;
            List<RankedMember> members = rankedMembers(period);
            for (int index = 0; index < Math.min(6, members.size()); index++) {
                var item = members.get(index);
                if (previous != item.points()) rank = index + 1;
                previous = item.points();
                var profile = item.profile();
                String initials = profile.getName().isEmpty() ? "" : profile.getName().substring(0, 1);
                rows.add(new PublicShowcase.RankingEntry(rank, profile.getId().toString(), profile.getName(),
                        initials, profile.getSkillTags().isEmpty() ? "" : profile.getSkillTags().getFirst(), item.points()));
            }
            boards.put(name, rows);
        }
        return boards;
    }

    @Transactional(readOnly = true)
    public int publicRankingCount() { return rankingParticipants().size(); }

    public record PreviewPage(List<PublicShowcase.RankingEntry> entries, int totalCount, int page, Instant updatedAt) {}

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public PreviewPage memberRankingPage(Authentication authentication, PointModels.LeaderboardPeriod period, int page) {
        if (authService.requireAccount(authentication).getRole() == Role.VISITOR)
            throw new ApiException(HttpStatus.FORBIDDEN, "完整榜单仅成员可查看");
        if (page < 0 || page > 1000000) throw new ApiException(HttpStatus.BAD_REQUEST, "页码无效");
        var members = rankedMembers(period);
        var rows = new ArrayList<PublicShowcase.RankingEntry>();
        int previous = Integer.MIN_VALUE;
        int rank = 0;
        for (int index = 0; index < members.size(); index++) {
            var item = members.get(index);
            if (previous != item.points()) rank = index + 1;
            previous = item.points();
            if (index < page * 25 || index >= (page + 1) * 25) continue;
            var profile = item.profile();
            rows.add(new PublicShowcase.RankingEntry(rank, profile.getId().toString(), profile.getName(),
                    profile.getName().isEmpty() ? "" : profile.getName().substring(0, 1),
                    profile.getSkillTags().isEmpty() ? "" : profile.getSkillTags().getFirst(), item.points()));
        }
        return new PreviewPage(rows, members.size(), page, Instant.now());
    }

    private List<MemberProfileEntity> rankingParticipants() {
        return profiles.findAll().stream()
                .filter(profile -> profile.getStatus() == MemberStatus.OFFICIAL)
                .filter(profile -> profile.getAccount().getRole() != Role.TEACHER)
                .toList();
    }

    @PreAuthorize("isAuthenticated()")
    public List<PointModels.RuleView> rules() {
        return java.util.Arrays.stream(PointSubcategory.values())
                .map(subcategory -> new PointModels.RuleView(
                        subcategory.category(),
                        subcategory.category().label(),
                        subcategory,
                        subcategory.label(),
                        subcategory.monthlyCap(),
                        subcategory.allocationPolicy()
                ))
                .toList();
    }

    private void validateAllocations(PointModels.GrantRequest request) {
        Set<UUID> memberIds = new HashSet<>();
        long allocatedTotal = 0;
        for (PointModels.AllocationRequest allocation : request.allocations()) {
            if (!memberIds.add(allocation.memberProfileId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "同一成员不能在一次发放中重复出现");
            }
            allocatedTotal = Math.addExact(allocatedTotal, allocation.points());
            if (request.subcategory().allocationPolicy() == PointSubcategory.AllocationPolicy.PER_MEMBER
                    && allocation.points() != request.itemTotalPoints()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "竞赛积分按成员个人发放，每位成员分值必须等于奖项分值");
            }
        }
        if (request.subcategory().allocationPolicy() == PointSubcategory.AllocationPolicy.SHARED_TOTAL
                && allocatedTotal != request.itemTotalPoints()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "成员分配积分之和必须等于任务总分");
        }
    }

    private void validateRecipient(AccountEntity operator, MemberProfileEntity member) {
        if (member.getAccount().getId().equals(operator.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "积分管理员不能给自己发放积分");
        }
        if (member.getAccount().getRole() == Role.TEACHER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "指导教师不参与成员积分统计");
        }
        if (member.getStatus() != MemberStatus.OFFICIAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "只能给正式成员发放积分");
        }
    }

    private int creditedPoints(
            MemberProfileEntity member,
            PointSubcategory subcategory,
            LocalDate occurredOn,
            int requestedPoints
    ) {
        Integer cap = subcategory.monthlyCap();
        if (cap == null) return requestedPoints;
        LocalDate start = occurredOn.withDayOfMonth(1);
        long current = entries.sumForMonth(member.getId(), subcategory, start, start.plusMonths(1));
        long remaining = cap - current;
        if (remaining <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    member.getName() + "在" + occurredOn.getYear() + "年" + occurredOn.getMonthValue()
                            + "月的“" + subcategory.label() + "”积分已达到 " + cap + " 分上限"
            );
        }
        return (int) Math.min(requestedPoints, remaining);
    }

    private PointModels.GrantView toGrantView(PointGrantEntity grant) {
        List<PointModels.AllocationView> allocations = grant.getEntries().stream()
                .map(entry -> new PointModels.AllocationView(
                        entry.getMember().getId(),
                        entry.getMember().getName(),
                        entry.getMember().getMemberCode(),
                        entry.getRequestedPoints(),
                        entry.getPoints(),
                        entry.getContribution(),
                        entry.getMember().getTotalPoints()
                ))
                .toList();
        int awardedPoints = allocations.stream().mapToInt(PointModels.AllocationView::creditedPoints).sum();
        PointSubcategory subcategory = grant.getSubcategory();
        return new PointModels.GrantView(
                grant.getId(),
                grant.getType(),
                grant.getReversalOf() == null ? null : grant.getReversalOf().getId(),
                subcategory.category(),
                subcategory.category().label(),
                subcategory,
                subcategory.label(),
                grant.getTitle(),
                grant.getOccurredOn(),
                grant.getItemTotalPoints(),
                awardedPoints,
                grant.getSourceReference(),
                grant.getEvidenceUrl(),
                grant.getDescription(),
                grant.getOperator().getUsername(),
                grant.getCreatedAt(),
                allocations,
                grant.getCompetitionId(), grant.getProjectId(),
                grant.getSourceEntityReference(), grant.getSourceName()
        );
    }

    private PointModels.EntryView toEntryView(PointEntryEntity entry) {
        PointGrantEntity grant = entry.getGrant();
        PointSubcategory subcategory = grant.getSubcategory();
        return new PointModels.EntryView(
                entry.getId(),
                grant.getId(),
                grant.getType(),
                grant.getReversalOf() == null ? null : grant.getReversalOf().getId(),
                subcategory.category(),
                subcategory.category().label(),
                subcategory,
                subcategory.label(),
                grant.getTitle(),
                grant.getOccurredOn(),
                entry.getRequestedPoints(),
                entry.getPoints(),
                entry.getContribution(),
                grant.getEvidenceUrl(),
                grant.getDescription(),
                grant.getOperator().getUsername(),
                entry.getCreatedAt()
        );
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String validateEvidenceUrl(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("/") && !normalized.startsWith("//")) return normalized;
        try {
            URI uri = URI.create(normalized);
            boolean supported = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
            if (!supported || uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException();
            return normalized;
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "积分凭证地址需使用 http、https 或站内绝对路径");
        }
    }

    private static String pointNotificationSummary(String title, LockedAllocation allocation) {
        String summary = title + "：+" + allocation.creditedPoints() + " 分";
        if (allocation.creditedPoints() < allocation.requestedPoints()) {
            summary += "（应得 " + allocation.requestedPoints() + " 分，按月度上限实际计入）";
        }
        return summary;
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static DateRange dateRange(PointModels.LeaderboardPeriod period, LocalDate today) {
        return switch (period) {
            case TOTAL -> new DateRange(LocalDate.of(1970, 1, 1), today.plusDays(1));
            case DAY -> new DateRange(today, today.plusDays(1));
            case WEEK -> {
                LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateRange(monday, monday.plusWeeks(1));
            }
            case MONTH -> new DateRange(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1));
            case YEAR -> new DateRange(today.withDayOfYear(1), today.withDayOfYear(1).plusYears(1));
        };
    }

    private record LockedAllocation(
            MemberProfileEntity member,
            int requestedPoints,
            int creditedPoints,
            String contribution
    ) {
    }

    /** 任务积分发放结果；{@code granted=false} 且原因非空表示按规则跳过计分。 */
    public record TaskGrantResult(boolean granted, UUID grantId, Integer creditedPoints, String skippedReason) {
    }

    private record RankedMember(MemberProfileEntity profile, int points) {
    }

    private record DateRange(LocalDate startInclusive, LocalDate endExclusive) {
    }
}
