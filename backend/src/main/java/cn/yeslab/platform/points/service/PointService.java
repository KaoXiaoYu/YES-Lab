package cn.yeslab.platform.points.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.notification.service.NotificationService;
import cn.yeslab.platform.points.api.PointModels;
import cn.yeslab.platform.points.model.PointCategory;
import cn.yeslab.platform.points.model.PointEntryEntity;
import cn.yeslab.platform.points.model.PointGrantEntity;
import cn.yeslab.platform.points.model.PointGrantType;
import cn.yeslab.platform.points.model.PointSubcategory;
import cn.yeslab.platform.points.repository.PointEntryRepository;
import cn.yeslab.platform.points.repository.PointGrantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PointService {

    private final PointGrantRepository grants;
    private final PointEntryRepository entries;
    private final MemberProfileRepository profiles;
    private final AuthService authService;
    private final NotificationService notifications;

    public PointService(
            PointGrantRepository grants,
            PointEntryRepository entries,
            MemberProfileRepository profiles,
            AuthService authService,
            NotificationService notifications
    ) {
        this.grants = grants;
        this.entries = entries;
        this.profiles = profiles;
        this.authService = authService;
        this.notifications = notifications;
    }

    @PreAuthorize("hasAuthority('POINTS_MANAGE')")
    @Transactional
    public PointModels.GrantView grant(Authentication authentication, PointModels.GrantRequest request) {
        AccountEntity operator = authService.requireAccount(authentication);
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
                validateEvidenceUrl(request.evidenceUrl()),
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
                "REVERSAL:" + original.getId(),
                normalize(request.evidenceUrl()) == null
                        ? original.getEvidenceUrl()
                        : validateEvidenceUrl(request.evidenceUrl()),
                request.reason().trim(),
                operator,
                original
        );
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
        return new PointModels.MemberSummary(profile.getTotalPoints(), categoryTotals, subcategoryTotals, recent);
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
                allocations
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

    private record LockedAllocation(
            MemberProfileEntity member,
            int requestedPoints,
            int creditedPoints,
            String contribution
    ) {
    }
}
