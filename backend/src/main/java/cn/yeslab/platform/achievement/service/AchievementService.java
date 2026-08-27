package cn.yeslab.platform.achievement.service;

import cn.yeslab.platform.achievement.api.AchievementModels;
import cn.yeslab.platform.achievement.model.CompetitionEntity;
import cn.yeslab.platform.achievement.model.CompetitionImageEntity;
import cn.yeslab.platform.achievement.model.CompetitionLifecycle;
import cn.yeslab.platform.achievement.model.CompetitionParticipantEntity;
import cn.yeslab.platform.achievement.model.NewsEntity;
import cn.yeslab.platform.achievement.model.VerificationStatus;
import cn.yeslab.platform.achievement.repository.CompetitionRepository;
import cn.yeslab.platform.achievement.repository.NewsRepository;
import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.project.model.ProjectTeamEntity;
import cn.yeslab.platform.project.repository.ProjectTeamRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class AchievementService {
    private static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private final CompetitionRepository competitions;
    private final NewsRepository news;
    private final MemberProfileRepository profiles;
    private final ProjectTeamRepository projects;
    private final AuthService authService;
    private final AchievementFileStorageService storage;

    public AchievementService(CompetitionRepository competitions, NewsRepository news,
                              MemberProfileRepository profiles, ProjectTeamRepository projects,
                              AuthService authService, AchievementFileStorageService storage) {
        this.competitions = competitions; this.news = news; this.profiles = profiles; this.projects = projects;
        this.authService = authService; this.storage = storage;
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public List<AchievementModels.CompetitionView> listCompetitions(Authentication authentication) {
        Actor actor = actor(authentication);
        return competitions.findAll().stream().filter(item -> canRead(item, actor))
                .sorted(Comparator.comparing(CompetitionEntity::getUpdatedAt).reversed())
                .map(item -> toView(item, actor)).toList();
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public AchievementModels.CompetitionView getCompetition(Authentication authentication, UUID id) {
        Actor actor = actor(authentication); CompetitionEntity item = requireCompetition(id);
        if (!canRead(item, actor)) throw new ApiException(HttpStatus.FORBIDDEN, "你无权查看该比赛记录");
        return toView(item, actor);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public List<AchievementModels.MemberOption> memberOptions() {
        return profiles.findAll().stream().filter(this::available)
                .sorted(Comparator.comparing(MemberProfileEntity::getName)).map(this::memberOption).toList();
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public List<AchievementModels.ProjectOption> projectOptions(Authentication authentication) {
        Actor actor = actor(authentication);
        return projects.findAll().stream().filter(project -> actor.systemAdmin() || projectContains(project, actor.profile().getId()))
                .sorted(Comparator.comparing(ProjectTeamEntity::getProjectName))
                .map(this::projectOption).toList();
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public AchievementModels.CompetitionView createCompetition(Authentication authentication,
            AchievementModels.CompetitionUpsertRequest request, MultipartFile certificate, List<MultipartFile> images) {
        Actor actor = actor(authentication);
        validateRequest(request, certificate != null && !certificate.isEmpty(), null);
        Advisor advisor = resolveAdvisor(request.advisorProfileId(), request.advisorName());
        ProjectTeamEntity project = resolveProject(request.projectId(), actor);
        CompetitionEntity item = new CompetitionEntity(request.name().trim(), request.level(), request.lifecycle(),
                request.description().trim(), actor.profile(), actor.account());
        item.updateDetails(request.name().trim(), normalize(request.track()), request.level(), request.lifecycle(),
                normalize(request.awardName()), request.description().trim(), request.competitionDate(),
                request.provincialDate(), request.nationalDate(), advisor.profile(), advisor.name(), project);
        item.replaceParticipants(resolveParticipants(request.participants(), actor.profile()));

        List<String> storedFiles = new ArrayList<>();
        try {
            if (certificate != null && !certificate.isEmpty()) {
                var stored = storage.storeCertificate(certificate); storedFiles.add("c:" + stored.storedName());
                item.updateCertificate(stored.storedName(), stored.originalName(), stored.contentType(), stored.sizeBytes(), false);
            }
            item.replaceImages(storeImages(request.name(), images, request.imageDescriptions(), storedFiles));
            return toView(competitions.save(item), actor);
        } catch (RuntimeException error) {
            storedFiles.forEach(this::deleteStoredMarker); throw error;
        }
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public AchievementModels.CompetitionView updateCompetition(Authentication authentication, UUID id,
            AchievementModels.CompetitionUpsertRequest request) {
        Actor actor = actor(authentication); CompetitionEntity item = requireCompetition(id);
        ensureCanEdit(item, actor); boolean wasFinished = item.getLifecycle() == CompetitionLifecycle.FINISHED;
        VerificationStatus previousStatus = item.getVerificationStatus();
        validateRequest(request, item.getCertificateStoredName() != null, item);
        Advisor advisor = resolveAdvisor(request.advisorProfileId(), request.advisorName());
        ProjectTeamEntity project = resolveProject(request.projectId(), actor);
        item.updateDetails(request.name().trim(), normalize(request.track()), request.level(), request.lifecycle(),
                normalize(request.awardName()), request.description().trim(), request.competitionDate(),
                request.provincialDate(), request.nationalDate(), advisor.profile(), advisor.name(), project);
        item.replaceParticipants(resolveParticipants(request.participants(), item.getCaptainProfile()));
        if (request.lifecycle() == CompetitionLifecycle.FINISHED
                && (!wasFinished || (!actor.systemAdmin() && previousStatus == VerificationStatus.REJECTED))) item.markPending();
        if (request.lifecycle() != CompetitionLifecycle.FINISHED) item.markNotRequired();
        return toView(competitions.save(item), actor);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public AchievementModels.CompetitionView replaceCertificate(Authentication authentication, UUID id, MultipartFile certificate) {
        Actor actor = actor(authentication); CompetitionEntity item = requireCompetition(id); ensureCanEdit(item, actor);
        var stored = storage.storeCertificate(certificate); String previous = item.getCertificateStoredName();
        item.updateCertificate(stored.storedName(), stored.originalName(), stored.contentType(), stored.sizeBytes(), true);
        CompetitionEntity saved = competitions.save(item); storage.deleteCertificate(previous); return toView(saved, actor);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public AchievementModels.CompetitionView replaceImages(Authentication authentication, UUID id,
            List<MultipartFile> images, List<String> descriptions) {
        Actor actor = actor(authentication); CompetitionEntity item = requireCompetition(id); ensureCanEdit(item, actor);
        if (images != null && images.size() > 8) throw new ApiException(HttpStatus.BAD_REQUEST, "比赛图片不能超过 8 张");
        List<String> markers = new ArrayList<>();
        List<String> previous = item.getImages().stream().map(CompetitionImageEntity::getStoredName).toList();
        try {
            item.replaceImages(storeImages(item.getName(), images, descriptions, markers));
            if (!actor.systemAdmin() && item.getLifecycle() == CompetitionLifecycle.FINISHED) item.markPending();
            CompetitionEntity saved = competitions.save(item); previous.forEach(storage::deleteImage); return toView(saved, actor);
        } catch (RuntimeException error) { markers.forEach(this::deleteStoredMarker); throw error; }
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public AchievementModels.CompetitionView deleteImage(Authentication authentication, UUID competitionId, UUID imageId) {
        Actor actor = actor(authentication); CompetitionEntity item = requireCompetition(competitionId); ensureCanEdit(item, actor);
        CompetitionImageEntity image = requireImage(item, imageId); String storedName = image.getStoredName();
        item.removeImage(image);
        if (!actor.systemAdmin() && item.getLifecycle() == CompetitionLifecycle.FINISHED) item.markPending();
        CompetitionEntity saved = competitions.save(item);
        storage.deleteImage(storedName);
        return toView(saved, actor);
    }

    @PreAuthorize("hasAuthority('ACHIEVEMENT_MANAGE')")
    @Transactional
    public AchievementModels.CompetitionView review(Authentication authentication, UUID id, AchievementModels.ReviewRequest request) {
        Actor actor = actor(authentication); CompetitionEntity item = requireCompetition(id);
        if (item.getLifecycle() != CompetitionLifecycle.FINISHED || item.getCertificateStoredName() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "只有已结束且上传证书的比赛可以审核");
        if (request.status() != VerificationStatus.APPROVED && request.status() != VerificationStatus.REJECTED)
            throw new ApiException(HttpStatus.BAD_REQUEST, "审核状态只能是通过或驳回");
        item.review(request.status(), normalize(request.note()), actor.account());
        return toView(competitions.save(item), actor);
    }

    @PreAuthorize("hasAuthority('ACHIEVEMENT_MANAGE')")
    @Transactional
    public AchievementModels.CompetitionView updateDisplay(Authentication authentication, UUID id, AchievementModels.DisplayRequest request) {
        Actor actor = actor(authentication); CompetitionEntity item = requireCompetition(id);
        if (item.getVerificationStatus() != VerificationStatus.APPROVED || item.getLifecycle() != CompetitionLifecycle.FINISHED)
            throw new ApiException(HttpStatus.BAD_REQUEST, "只有审核通过的已结束比赛可以公开展示");
        item.updateDisplay(request.featured(), request.displayOrder());
        return toView(competitions.save(item), actor);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public FileDownload certificate(Authentication authentication, UUID id) {
        Actor actor = actor(authentication); CompetitionEntity item = requireCompetition(id);
        if (!actor.systemAdmin() && !item.getCaptainProfile().getId().equals(actor.profile().getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "只有队长和管理员可以查看证书");
        if (item.getCertificateStoredName() == null) throw new ApiException(HttpStatus.NOT_FOUND, "该比赛尚未上传证书");
        return new FileDownload(storage.certificate(item.getCertificateStoredName()), item.getCertificateOriginalName(), item.getCertificateContentType());
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public FileDownload internalImage(Authentication authentication, UUID competitionId, UUID imageId) {
        Actor actor = actor(authentication); CompetitionEntity item = requireCompetition(competitionId);
        if (!canRead(item, actor)) throw new ApiException(HttpStatus.FORBIDDEN, "你无权查看该图片");
        CompetitionImageEntity image = requireImage(item, imageId);
        return new FileDownload(storage.image(image.getStoredName()), image.getOriginalName(), image.getContentType());
    }

    @Transactional(readOnly = true)
    public FileDownload publicImage(UUID competitionId, UUID imageId) {
        CompetitionEntity item = requireCompetition(competitionId);
        ensurePublic(item); CompetitionImageEntity image = requireImage(item, imageId);
        return new FileDownload(storage.image(image.getStoredName()), image.getOriginalName(), image.getContentType());
    }

    @Transactional(readOnly = true)
    public FileDownload publicCertificate(UUID competitionId) {
        CompetitionEntity item = requireCompetition(competitionId);
        ensurePublic(item);
        if (item.getCertificateStoredName() == null) throw new ApiException(HttpStatus.NOT_FOUND, "该比赛尚未上传证书");
        return new FileDownload(storage.certificate(item.getCertificateStoredName()), item.getCertificateOriginalName(), item.getCertificateContentType());
    }

    @Transactional(readOnly = true)
    public List<AchievementModels.PublicCompetitionView> publicCompetitions() {
        return competitions.findAll().stream().filter(this::isPublicFeatured)
                .sorted(Comparator.comparingInt(CompetitionEntity::getDisplayOrder).thenComparing(CompetitionEntity::getCompetitionDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toPublicView).toList();
    }

    @Transactional(readOnly = true)
    public AchievementModels.PublicCompetitionView publicCompetition(UUID id) {
        CompetitionEntity item = requireCompetition(id); ensurePublic(item); return toPublicView(item);
    }

    @Transactional(readOnly = true)
    public AchievementModels.CompetitionCountdownView publicUpcomingCompetition() {
        return nearestUpcoming(item -> true);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public AchievementModels.CompetitionCountdownView ownUpcomingCompetition(Authentication authentication) {
        Actor actor = actor(authentication);
        UUID profileId = actor.profile().getId();
        return nearestUpcoming(item -> item.getCaptainProfile().getId().equals(profileId)
                || item.getAdvisorProfile() != null && item.getAdvisorProfile().getId().equals(profileId)
                || item.getParticipants().stream().anyMatch(participant -> participant.getLinkedProfile() != null
                && participant.getLinkedProfile().getId().equals(profileId)));
    }

    @PreAuthorize("hasAuthority('CONTENT_MANAGE')") @Transactional(readOnly = true)
    public List<AchievementModels.NewsView> managedNews() {
        return news.findAll().stream().sorted(Comparator.comparing(NewsEntity::getPublishedDate).reversed()).map(this::newsView).toList();
    }
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')") @Transactional
    public AchievementModels.NewsView createNews(Authentication authentication, AchievementModels.NewsRequest request) {
        AccountEntity account = authService.requireAccount(authentication); validateUrl(request.sourceUrl());
        return newsView(news.save(new NewsEntity(request.title().trim(), request.sourceName().trim(), request.sourceUrl().trim(),
                request.summary().trim(), request.publishedDate(), request.visible(), account)));
    }
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')") @Transactional
    public AchievementModels.NewsView updateNews(UUID id, AchievementModels.NewsRequest request) {
        NewsEntity item = news.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "新闻不存在")); validateUrl(request.sourceUrl());
        item.update(request.title().trim(), request.sourceName().trim(), request.sourceUrl().trim(), request.summary().trim(), request.publishedDate(), request.visible());
        return newsView(news.save(item));
    }
    @Transactional(readOnly = true)
    public List<AchievementModels.NewsView> publicNews() {
        return news.findAll().stream().filter(NewsEntity::isVisible).sorted(Comparator.comparing(NewsEntity::getPublishedDate).reversed()).map(this::newsView).toList();
    }

    @Transactional(readOnly = true)
    public List<String> approvedAchievementsFor(UUID profileId) {
        return approvedAchievementOptionsFor(profileId).stream()
                .map(item -> item.name() + (item.awardName() == null ? "" : " · " + item.awardName())).toList();
    }

    @Transactional(readOnly = true)
    public List<AchievementModels.CompetitionShowcaseOption> approvedAchievementOptionsFor(UUID profileId) {
        return competitions.findAll().stream()
                .filter(item -> item.getLifecycle() == CompetitionLifecycle.FINISHED && item.getVerificationStatus() == VerificationStatus.APPROVED)
                .filter(item -> item.getParticipants().stream().anyMatch(p -> p.getLinkedProfile() != null && p.getLinkedProfile().getId().equals(profileId)))
                .sorted(Comparator.comparing(CompetitionEntity::getCompetitionDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(item -> new AchievementModels.CompetitionShowcaseOption(item.getId(), item.getName(), item.getAwardName(), item.getCompetitionDate()))
                .toList();
    }

    private void validateRequest(AchievementModels.CompetitionUpsertRequest request, boolean hasCertificate, CompetitionEntity existing) {
        if (request.lifecycle() == CompetitionLifecycle.FINISHED) {
            if (!hasCertificate) throw new ApiException(HttpStatus.BAD_REQUEST, "已结束比赛必须上传证书");
            if (normalize(request.awardName()) == null) throw new ApiException(HttpStatus.BAD_REQUEST, "请填写获奖结果");
            if (request.competitionDate() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "请填写比赛或获奖日期");
        } else {
            if (request.provincialDate() == null || request.nationalDate() == null)
                throw new ApiException(HttpStatus.BAD_REQUEST, "未结束比赛需要填写省赛和国赛时间");
            if (request.nationalDate().isBefore(request.provincialDate()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "国赛时间不能早于省赛时间");
            if (normalize(request.advisorName()) == null && request.advisorProfileId() == null)
                throw new ApiException(HttpStatus.BAD_REQUEST, "未结束比赛需要填写指导老师");
        }
    }

    private List<CompetitionParticipantEntity> resolveParticipants(List<AchievementModels.ParticipantRequest> requests, MemberProfileEntity captain) {
        List<CompetitionParticipantEntity> result = new ArrayList<>();
        result.add(new CompetitionParticipantEntity(captain.getName(), captain, true, 0));
        Set<UUID> linked = new HashSet<>(); linked.add(captain.getId()); int order = 1;
        if (requests != null) for (var request : requests) {
            MemberProfileEntity profile = request.linkedProfileId() == null ? null : requireAvailableProfile(request.linkedProfileId());
            if (profile != null && !linked.add(profile.getId())) continue;
            String name = profile == null ? request.displayName().trim() : profile.getName();
            if (name.equals(captain.getName()) && profile == null) continue;
            result.add(new CompetitionParticipantEntity(name, profile, false, order++));
        }
        return result;
    }

    private Advisor resolveAdvisor(UUID profileId, String suppliedName) {
        if (profileId == null) return new Advisor(null, normalize(suppliedName));
        MemberProfileEntity profile = requireAvailableProfile(profileId);
        if (profile.getAccount().getRole() != Role.TEACHER) throw new ApiException(HttpStatus.BAD_REQUEST, "关联的指导老师必须是教师账号");
        return new Advisor(profile, profile.getName());
    }

    private ProjectTeamEntity resolveProject(UUID projectId, Actor actor) {
        if (projectId == null) return null;
        ProjectTeamEntity project = projects.findById(projectId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "关联项目不存在"));
        if (!actor.systemAdmin() && !projectContains(project, actor.profile().getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "只能关联自己参与的项目");
        return project;
    }

    private boolean projectContains(ProjectTeamEntity project, UUID profileId) {
        return project.getLeader().getId().equals(profileId) || project.getMembers().stream().anyMatch(p -> p.getId().equals(profileId));
    }

    private List<CompetitionImageEntity> storeImages(String name, List<MultipartFile> files, List<String> descriptions, List<String> markers) {
        if (files == null || files.isEmpty()) return List.of();
        if (files.size() > 8) throw new ApiException(HttpStatus.BAD_REQUEST, "比赛图片不能超过 8 张");
        List<CompetitionImageEntity> result = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            var stored = storage.storeImage(files.get(i)); markers.add("i:" + stored.storedName());
            String description = descriptions != null && i < descriptions.size() ? normalize(descriptions.get(i)) : null;
            if (description == null) description = name.trim() + "比赛图片 " + (i + 1);
            result.add(new CompetitionImageEntity(stored.storedName(), stored.originalName(), stored.contentType(), stored.sizeBytes(), description, i));
        }
        return result;
    }

    private AchievementModels.CompetitionCountdownView nearestUpcoming(Predicate<CompetitionEntity> visibility) {
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        return competitions.findAll().stream()
                .filter(item -> item.getLifecycle() != CompetitionLifecycle.FINISHED)
                .filter(visibility)
                .map(item -> countdownCandidate(item, today))
                .filter(candidate -> candidate != null)
                .min(Comparator.comparing(AchievementModels.CompetitionCountdownView::date)
                        .thenComparing(AchievementModels.CompetitionCountdownView::name))
                .orElse(null);
    }

    private AchievementModels.CompetitionCountdownView countdownCandidate(CompetitionEntity item, LocalDate today) {
        if (item.getProvincialDate() != null && !item.getProvincialDate().isBefore(today)) {
            return new AchievementModels.CompetitionCountdownView(item.getId(), item.getName(), item.getTrack(), "省赛", item.getProvincialDate());
        }
        if (item.getNationalDate() != null && !item.getNationalDate().isBefore(today)) {
            return new AchievementModels.CompetitionCountdownView(item.getId(), item.getName(), item.getTrack(), "国赛", item.getNationalDate());
        }
        return null;
    }

    private void deleteStoredMarker(String marker) { if (marker.startsWith("c:")) storage.deleteCertificate(marker.substring(2)); else storage.deleteImage(marker.substring(2)); }
    private Actor actor(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        MemberProfileEntity profile = profiles.findByAccountId(account.getId()).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "当前账号不是实验室成员"));
        return new Actor(account, profile, account.getRole().isSystemAdmin());
    }
    private boolean canRead(CompetitionEntity item, Actor actor) {
        return actor.systemAdmin() || item.getVerificationStatus() == VerificationStatus.APPROVED
                || item.getCaptainProfile().getId().equals(actor.profile().getId())
                || item.getParticipants().stream().anyMatch(p -> p.getLinkedProfile() != null && p.getLinkedProfile().getId().equals(actor.profile().getId()));
    }
    private void ensureCanEdit(CompetitionEntity item, Actor actor) {
        if (actor.systemAdmin()) return;
        if (!item.getCaptainProfile().getId().equals(actor.profile().getId())) throw new ApiException(HttpStatus.FORBIDDEN, "只有队长或管理员可以修改比赛");
        if (item.getVerificationStatus() == VerificationStatus.APPROVED) throw new ApiException(HttpStatus.FORBIDDEN, "审核通过后仅管理员可以修改");
    }
    private boolean isPublicFeatured(CompetitionEntity item) { return isApprovedFinished(item) && item.isFeatured(); }
    private boolean isApprovedFinished(CompetitionEntity item) { return item.getLifecycle() == CompetitionLifecycle.FINISHED && item.getVerificationStatus() == VerificationStatus.APPROVED; }
    private void ensurePublic(CompetitionEntity item) { if (!isApprovedFinished(item)) throw new ApiException(HttpStatus.NOT_FOUND, "该比赛成果暂未公开"); }
    private CompetitionEntity requireCompetition(UUID id) { return competitions.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "比赛不存在")); }
    private CompetitionImageEntity requireImage(CompetitionEntity item, UUID imageId) { return item.getImages().stream().filter(i -> i.getId().equals(imageId)).findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "比赛图片不存在")); }
    private MemberProfileEntity requireAvailableProfile(UUID id) { MemberProfileEntity profile = profiles.findById(id).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "关联成员不存在")); if (!available(profile)) throw new ApiException(HttpStatus.BAD_REQUEST, "只能关联试用或正式成员"); return profile; }
    private boolean available(MemberProfileEntity profile) { return profile.getStatus() == MemberStatus.TRIAL || profile.getStatus() == MemberStatus.OFFICIAL; }
    private void validateUrl(String value) { if (!value.startsWith("https://") && !value.startsWith("http://")) throw new ApiException(HttpStatus.BAD_REQUEST, "新闻原文链接需使用 http 或 https"); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private AchievementModels.CompetitionView toView(CompetitionEntity item, Actor actor) {
        return new AchievementModels.CompetitionView(item.getId(), item.getName(), item.getTrack(), item.getLevel(), item.getLifecycle(), item.getAwardName(), item.getDescription(),
                item.getCompetitionDate(), item.getProvincialDate(), item.getNationalDate(), memberOption(item.getCaptainProfile()),
                item.getAdvisorProfile() == null ? null : memberOption(item.getAdvisorProfile()), item.getAdvisorName(), projectOption(item.getProject()),
                participantViews(item), imageViews(item, false), item.getVerificationStatus(), item.getReviewNote(),
                item.getReviewer() == null ? null : item.getReviewer().getUsername(), item.getReviewedAt(), item.isFeatured(), item.getDisplayOrder(),
                item.getCertificateStoredName() != null, item.getCertificateOriginalName(), canEdit(item, actor), actor.systemAdmin(), item.getCreatedAt(), item.getUpdatedAt());
    }
    private boolean canEdit(CompetitionEntity item, Actor actor) { return actor.systemAdmin() || item.getCaptainProfile().getId().equals(actor.profile().getId()) && item.getVerificationStatus() != VerificationStatus.APPROVED; }
    private AchievementModels.PublicCompetitionView toPublicView(CompetitionEntity item) {
        return new AchievementModels.PublicCompetitionView(item.getId(), item.getName(), item.getTrack(), item.getLevel(), item.getAwardName(), item.getDescription(), item.getCompetitionDate(),
                publicMemberOption(item.getCaptainProfile()), item.getAdvisorProfile() == null ? null : publicMemberOption(item.getAdvisorProfile()), item.getAdvisorName(), projectOption(item.getProject()),
                participantViews(item), imageViews(item, true), item.getCertificateStoredName() != null, item.getCertificateOriginalName(), item.getCertificateContentType(),
                item.getCertificateStoredName() == null ? null : "/api/v1/public/competitions/" + item.getId() + "/certificate",
                item.getDisplayOrder(), item.getUpdatedAt());
    }
    private List<AchievementModels.ParticipantView> participantViews(CompetitionEntity item) { return item.getParticipants().stream().map(p -> new AchievementModels.ParticipantView(p.getDisplayName(), p.getLinkedProfile() == null ? null : p.getLinkedProfile().getId(), p.getLinkedProfile() == null ? null : p.getLinkedProfile().getAvatarUrl(), p.isCaptain())).toList(); }
    private List<AchievementModels.ImageView> imageViews(CompetitionEntity item, boolean publicPath) { return item.getImages().stream().map(image -> new AchievementModels.ImageView(image.getId(), (publicPath ? "/api/v1/public/competitions/" : "/api/v1/competitions/") + item.getId() + "/images/" + image.getId(), image.getDescription(), image.getDisplayOrder())).toList(); }
    private AchievementModels.MemberOption memberOption(MemberProfileEntity p) { return new AchievementModels.MemberOption(p.getId(), p.getName(), p.getMemberCode(), p.getAccount().getRole(), p.getAvatarUrl()); }
    private AchievementModels.MemberOption publicMemberOption(MemberProfileEntity p) { return new AchievementModels.MemberOption(p.getId(), p.getName(), null, p.getAccount().getRole(), p.getAvatarUrl()); }
    private AchievementModels.ProjectOption projectOption(ProjectTeamEntity p) { return p == null ? null : new AchievementModels.ProjectOption(p.getId(), p.getProjectName(), p.getTeamName()); }
    private AchievementModels.NewsView newsView(NewsEntity item) { return new AchievementModels.NewsView(item.getId(), item.getTitle(), item.getSourceName(), item.getSourceUrl(), item.getSummary(), item.getPublishedDate(), item.isVisible(), item.getCreatedAt(), item.getUpdatedAt()); }

    private record Actor(AccountEntity account, MemberProfileEntity profile, boolean systemAdmin) {}
    private record Advisor(MemberProfileEntity profile, String name) {}
    public record FileDownload(Resource resource, String originalName, String contentType) {}
}
