package cn.yeslab.platform.project.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.project.api.ProjectModels;
import cn.yeslab.platform.project.model.ProjectStatus;
import cn.yeslab.platform.project.model.ProjectTeamEntity;
import cn.yeslab.platform.project.repository.ProjectTeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectTeamService {

    private final ProjectTeamRepository projects;
    private final MemberProfileRepository profiles;
    private final AuthService authService;
    private final ProjectCoverStorageService coverStorage;

    public ProjectTeamService(
            ProjectTeamRepository projects,
            MemberProfileRepository profiles,
            AuthService authService,
            ProjectCoverStorageService coverStorage
    ) {
        this.projects = projects;
        this.profiles = profiles;
        this.authService = authService;
        this.coverStorage = coverStorage;
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public List<ProjectModels.ProjectView> listProjects(Authentication authentication) {
        Actor actor = actor(authentication);
        return projects.findAll().stream()
                .filter(project -> canRead(project, actor))
                .sorted(Comparator.comparing(ProjectTeamEntity::getUpdatedAt).reversed())
                .map(project -> toView(project, actor))
                .toList();
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public ProjectModels.ProjectView getProject(Authentication authentication, UUID projectId) {
        Actor actor = actor(authentication);
        ProjectTeamEntity project = requireProject(projectId);
        if (!canRead(project, actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "你不是该项目团队的成员");
        }
        return toView(project, actor);
    }

    @PreAuthorize("hasAuthority('PROJECT_MANAGE')")
    @Transactional
    public ProjectModels.ProjectView createProject(
            Authentication authentication,
            ProjectModels.CreateProjectRequest request
    ) {
        Actor actor = actor(authentication);
        validateDates(request.startDate(), request.endDate());
        MemberProfileEntity leader = requireAvailableMember(request.leaderProfileId());
        MemberProfileEntity advisor = resolveAdvisor(request.advisorProfileId());
        Set<MemberProfileEntity> members = resolveMembers(request.memberProfileIds());
        members.add(leader);
        Set<MemberProfileEntity> administrators = resolveMembers(request.administratorProfileIds());
        ensureAdministratorsAreMembers(administrators, members);

        ProjectTeamEntity project = new ProjectTeamEntity(
                request.projectName().trim(),
                request.teamName().trim(),
                request.description().trim(),
                request.type(),
                request.status(),
                leader,
                advisor,
                actor.account()
        );
        project.updateDetails(
                request.projectName().trim(),
                request.description().trim(),
                request.type(),
                request.status(),
                advisor,
                normalizeList(request.requiredSkillTags()),
                request.startDate(),
                request.endDate(),
                normalizeList(request.stageGoals()),
                normalize(request.progressDescription()),
                normalize(request.outcomes()),
                normalizeUrl(request.gitRepositoryUrl(), "Git 仓库"),
                normalizeUrl(request.documentUrl(), "文档"),
                request.externallyVisible()
        );
        project.updateTeam(request.teamName().trim(), leader, members, administrators);
        return toView(projects.save(project), actor);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public ProjectModels.ProjectView updateProject(
            Authentication authentication,
            UUID projectId,
            ProjectModels.UpdateProjectRequest request
    ) {
        Actor actor = actor(authentication);
        ProjectTeamEntity project = requireProject(projectId);
        if (!canEdit(project, actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有项目负责人、项目管理员或系统管理员可以修改项目资料");
        }
        validateDates(request.startDate(), request.endDate());
        project.updateDetails(
                request.projectName().trim(),
                request.description().trim(),
                request.type(),
                request.status(),
                resolveAdvisor(request.advisorProfileId()),
                normalizeList(request.requiredSkillTags()),
                request.startDate(),
                request.endDate(),
                normalizeList(request.stageGoals()),
                normalize(request.progressDescription()),
                normalize(request.outcomes()),
                normalizeUrl(request.gitRepositoryUrl(), "Git 仓库"),
                normalizeUrl(request.documentUrl(), "文档"),
                request.externallyVisible()
        );
        return toView(projects.save(project), actor);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public ProjectModels.ProjectView updateTeam(
            Authentication authentication,
            UUID projectId,
            ProjectModels.UpdateTeamRequest request
    ) {
        Actor actor = actor(authentication);
        ProjectTeamEntity project = requireProject(projectId);
        if (!canManageTeam(project, actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有项目负责人或系统管理员可以管理团队成员");
        }

        if (!actor.systemAdmin() && !project.getLeader().getId().equals(request.leaderProfileId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有系统管理员可以更换项目负责人");
        }
        MemberProfileEntity leader = requireAvailableMember(request.leaderProfileId());
        Set<MemberProfileEntity> members = resolveMembers(request.memberProfileIds());
        members.add(leader);
        Set<MemberProfileEntity> administrators = resolveMembers(request.administratorProfileIds());
        ensureAdministratorsAreMembers(administrators, members);
        project.updateTeam(request.teamName().trim(), leader, members, administrators);
        return toView(projects.save(project), actor);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public ProjectModels.ProjectView replaceCover(
            Authentication authentication,
            UUID projectId,
            MultipartFile cover
    ) {
        Actor actor = actor(authentication);
        ProjectTeamEntity project = requireProject(projectId);
        if (!canEdit(project, actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有项目负责人、项目管理员或系统管理员可以上传项目主图");
        }

        ProjectCoverStorageService.StoredCover stored = coverStorage.store(cover);
        String previousStoredName = project.getCoverStoredName();
        project.updateCover(stored.storedName(), stored.originalName(), stored.contentType(), stored.sizeBytes());
        try {
            ProjectTeamEntity saved = projects.saveAndFlush(project);
            coverStorage.delete(previousStoredName);
            return toView(saved, actor);
        } catch (RuntimeException error) {
            coverStorage.delete(stored.storedName());
            throw error;
        }
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public CoverDownload internalCover(Authentication authentication, UUID projectId) {
        Actor actor = actor(authentication);
        ProjectTeamEntity project = requireProject(projectId);
        if (!canRead(project, actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "你无权查看该项目主图");
        }
        return coverDownload(project);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public List<ProjectModels.MemberSummary> memberOptions() {
        return profiles.findAll().stream()
                .filter(this::isAvailableMember)
                .sorted(Comparator.comparing(MemberProfileEntity::getName))
                .map(this::toMemberSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectModels.PublicProjectView> listPublicProjects() {
        return projects.findAll().stream()
                .filter(ProjectTeamEntity::isExternallyVisible)
                .filter(project -> project.getStatus() != ProjectStatus.ARCHIVED)
                .sorted(Comparator.comparing(ProjectTeamEntity::getUpdatedAt).reversed())
                .map(this::toPublicView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectModels.PublicProjectView getPublicProject(UUID projectId) {
        ProjectTeamEntity project = requireProject(projectId);
        if (!project.isExternallyVisible() || project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "该项目暂未对外展示");
        }
        return toPublicView(project);
    }

    @Transactional(readOnly = true)
    public CoverDownload publicCover(UUID projectId) {
        ProjectTeamEntity project = requireProject(projectId);
        if (!project.isExternallyVisible() || project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "该项目暂未对外展示");
        }
        return coverDownload(project);
    }

    private CoverDownload coverDownload(ProjectTeamEntity project) {
        if (project.getCoverStoredName() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "该项目正在使用默认主图");
        }
        return new CoverDownload(
                coverStorage.resource(project.getCoverStoredName()),
                project.getCoverOriginalName(),
                project.getCoverContentType()
        );
    }

    private Actor actor(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        UUID profileId = profiles.findByAccountId(account.getId()).map(MemberProfileEntity::getId).orElse(null);
        return new Actor(account, profileId, account.getRole().isSystemAdmin());
    }

    private ProjectTeamEntity requireProject(UUID projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "项目不存在"));
    }

    private MemberProfileEntity requireAvailableMember(UUID profileId) {
        MemberProfileEntity profile = profiles.findById(profileId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "选择的成员不存在"));
        if (!isAvailableMember(profile)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "只能选择试用或正式状态的实验室成员");
        }
        return profile;
    }

    private boolean isAvailableMember(MemberProfileEntity profile) {
        return profile.getStatus() == MemberStatus.OFFICIAL || profile.getStatus() == MemberStatus.TRIAL;
    }

    private MemberProfileEntity resolveAdvisor(UUID profileId) {
        if (profileId == null) return null;
        MemberProfileEntity advisor = requireAvailableMember(profileId);
        if (advisor.getAccount().getRole() != Role.TEACHER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "项目指导老师必须选择教师账号");
        }
        return advisor;
    }

    private Set<MemberProfileEntity> resolveMembers(Set<UUID> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) return new LinkedHashSet<>();
        List<MemberProfileEntity> resolved = profiles.findAllById(profileIds);
        if (resolved.size() != profileIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "成员列表中存在无效账号");
        }
        resolved.forEach(profile -> {
            if (!isAvailableMember(profile)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "团队只能包含试用或正式状态的成员");
            }
        });
        return new LinkedHashSet<>(resolved);
    }

    private void ensureAdministratorsAreMembers(
            Set<MemberProfileEntity> administrators,
            Set<MemberProfileEntity> members
    ) {
        Set<UUID> memberIds = members.stream().map(MemberProfileEntity::getId).collect(java.util.stream.Collectors.toSet());
        if (administrators.stream().map(MemberProfileEntity::getId).anyMatch(id -> !memberIds.contains(id))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "项目管理员必须先加入项目成员");
        }
    }

    private boolean canRead(ProjectTeamEntity project, Actor actor) {
        return actor.systemAdmin() || containsProfile(project, actor.profileId());
    }

    private boolean canEdit(ProjectTeamEntity project, Actor actor) {
        if (actor.systemAdmin()) return true;
        if (actor.profileId() == null) return false;
        return project.getLeader().getId().equals(actor.profileId())
                || project.getAdministrators().stream().anyMatch(member -> member.getId().equals(actor.profileId()));
    }

    private boolean canManageTeam(ProjectTeamEntity project, Actor actor) {
        return actor.systemAdmin()
                || actor.profileId() != null && project.getLeader().getId().equals(actor.profileId());
    }

    private boolean containsProfile(ProjectTeamEntity project, UUID profileId) {
        if (profileId == null) return false;
        return project.getLeader().getId().equals(profileId)
                || project.getMembers().stream().anyMatch(member -> member.getId().equals(profileId))
                || project.getAdministrators().stream().anyMatch(member -> member.getId().equals(profileId));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束时间不能早于开始时间");
        }
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.stream().map(this::normalize).filter(value -> value != null).forEach(normalized::add);
        return List.copyOf(normalized);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String normalizeUrl(String value, String label) {
        String normalized = normalize(value);
        if (normalized == null) return null;
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "地址需使用 http 或 https");
        }
        return normalized;
    }

    private ProjectModels.ProjectView toView(ProjectTeamEntity project, Actor actor) {
        return new ProjectModels.ProjectView(
                project.getId(),
                project.getProjectName(),
                project.getTeamName(),
                project.getDescription(),
                project.getType(),
                project.getStatus(),
                toMemberSummary(project.getLeader()),
                project.getAdvisor() == null ? null : toMemberSummary(project.getAdvisor()),
                sortedMembers(project.getMembers()),
                sortedMembers(project.getAdministrators()),
                project.getRequiredSkillTags(),
                project.getStartDate(),
                project.getEndDate(),
                project.getStageGoals(),
                project.getProgressDescription(),
                project.getOutcomes(),
                project.getGitRepositoryUrl(),
                project.getDocumentUrl(),
                project.getCoverStoredName() == null ? null : "/api/v1/projects/" + project.getId() + "/cover",
                project.getCoverOriginalName(),
                project.isExternallyVisible(),
                canEdit(project, actor),
                canManageTeam(project, actor),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private ProjectModels.PublicProjectView toPublicView(ProjectTeamEntity project) {
        return new ProjectModels.PublicProjectView(
                project.getId(),
                project.getProjectName(),
                project.getTeamName(),
                project.getDescription(),
                project.getType(),
                project.getStatus(),
                toPublicMemberSummary(project.getLeader()),
                project.getAdvisor() == null ? null : toPublicMemberSummary(project.getAdvisor()),
                sortedPublicMembers(project.getMembers()),
                project.getRequiredSkillTags(),
                project.getStartDate(),
                project.getEndDate(),
                project.getStageGoals(),
                project.getProgressDescription(),
                project.getOutcomes(),
                project.getGitRepositoryUrl(),
                project.getDocumentUrl(),
                project.getCoverStoredName() == null ? null : "/api/v1/public/project-teams/" + project.getId() + "/cover",
                project.getUpdatedAt()
        );
    }

    private List<ProjectModels.MemberSummary> sortedMembers(Set<MemberProfileEntity> members) {
        return members.stream()
                .sorted(Comparator.comparing(MemberProfileEntity::getName))
                .map(this::toMemberSummary)
                .toList();
    }

    private List<ProjectModels.MemberSummary> sortedPublicMembers(Set<MemberProfileEntity> members) {
        return members.stream()
                .sorted(Comparator.comparing(MemberProfileEntity::getName))
                .map(this::toPublicMemberSummary)
                .toList();
    }

    private ProjectModels.MemberSummary toMemberSummary(MemberProfileEntity profile) {
        return new ProjectModels.MemberSummary(
                profile.getId(),
                profile.getName(),
                profile.getMemberCode(),
                profile.getAccount().getRole(),
                profile.getAvatarUrl(),
                profile.getSkillTags()
        );
    }

    private ProjectModels.MemberSummary toPublicMemberSummary(MemberProfileEntity profile) {
        return new ProjectModels.MemberSummary(
                profile.getId(),
                profile.getName(),
                null,
                profile.getAccount().getRole(),
                profile.getAvatarUrl(),
                profile.getSkillTags()
        );
    }

    private record Actor(AccountEntity account, UUID profileId, boolean systemAdmin) {
    }

    public record CoverDownload(Resource resource, String originalName, String contentType) {
    }
}
