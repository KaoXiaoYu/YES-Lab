package cn.yeslab.platform.project.api;

import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.project.model.ProjectStatus;
import cn.yeslab.platform.project.model.ProjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ProjectModels {

    private ProjectModels() {
    }

    public record MemberSummary(
            UUID id,
            String name,
            String memberCode,
            Role role,
            String avatarUrl,
            List<String> skillTags
    ) {
    }

    public record ProjectView(
            UUID id,
            String projectName,
            String teamName,
            String description,
            ProjectType type,
            ProjectStatus status,
            MemberSummary leader,
            MemberSummary advisor,
            List<MemberSummary> members,
            List<MemberSummary> administrators,
            List<String> requiredSkillTags,
            LocalDate startDate,
            LocalDate endDate,
            List<String> stageGoals,
            String progressDescription,
            String outcomes,
            String gitRepositoryUrl,
            String documentUrl,
            String coverImageUrl,
            String coverImageOriginalName,
            boolean externallyVisible,
            boolean canEditProject,
            boolean canManageTeam,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PublicProjectView(
            UUID id,
            String projectName,
            String teamName,
            String description,
            ProjectType type,
            ProjectStatus status,
            MemberSummary leader,
            MemberSummary advisor,
            List<MemberSummary> members,
            List<String> requiredSkillTags,
            LocalDate startDate,
            LocalDate endDate,
            List<String> stageGoals,
            String progressDescription,
            String outcomes,
            String gitRepositoryUrl,
            String documentUrl,
            String coverImageUrl,
            Instant updatedAt
    ) {
    }

    public record CreateProjectRequest(
            @NotBlank(message = "请输入项目名称") @Size(max = 160) String projectName,
            @NotBlank(message = "请输入团队名称") @Size(max = 120) String teamName,
            @NotBlank(message = "请输入项目简介") @Size(max = 5000) String description,
            @NotNull(message = "请选择项目类型") ProjectType type,
            @NotNull(message = "请选择项目状态") ProjectStatus status,
            @NotNull(message = "请选择项目负责人") UUID leaderProfileId,
            UUID advisorProfileId,
            @Size(max = 100, message = "项目成员不能超过 100 人") Set<UUID> memberProfileIds,
            @Size(max = 20, message = "项目管理员不能超过 20 人") Set<UUID> administratorProfileIds,
            @Size(max = 20, message = "所需能力标签不能超过 20 个") List<@NotBlank @Size(max = 80) String> requiredSkillTags,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 30, message = "阶段目标不能超过 30 项") List<@NotBlank @Size(max = 500) String> stageGoals,
            @Size(max = 10000, message = "进度说明不能超过 10000 个字符") String progressDescription,
            @Size(max = 10000, message = "成果说明不能超过 10000 个字符") String outcomes,
            @Size(max = 500, message = "Git 仓库地址不能超过 500 个字符") String gitRepositoryUrl,
            @Size(max = 500, message = "文档地址不能超过 500 个字符") String documentUrl,
            boolean externallyVisible
    ) {
    }

    public record UpdateProjectRequest(
            @NotBlank(message = "请输入项目名称") @Size(max = 160) String projectName,
            @NotBlank(message = "请输入项目简介") @Size(max = 5000) String description,
            @NotNull(message = "请选择项目类型") ProjectType type,
            @NotNull(message = "请选择项目状态") ProjectStatus status,
            UUID advisorProfileId,
            @Size(max = 20, message = "所需能力标签不能超过 20 个") List<@NotBlank @Size(max = 80) String> requiredSkillTags,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 30, message = "阶段目标不能超过 30 项") List<@NotBlank @Size(max = 500) String> stageGoals,
            @Size(max = 10000, message = "进度说明不能超过 10000 个字符") String progressDescription,
            @Size(max = 10000, message = "成果说明不能超过 10000 个字符") String outcomes,
            @Size(max = 500, message = "Git 仓库地址不能超过 500 个字符") String gitRepositoryUrl,
            @Size(max = 500, message = "文档地址不能超过 500 个字符") String documentUrl,
            boolean externallyVisible
    ) {
    }

    public record UpdateTeamRequest(
            @NotBlank(message = "请输入团队名称") @Size(max = 120) String teamName,
            @NotNull(message = "项目负责人不能为空") UUID leaderProfileId,
            @Size(max = 100, message = "项目成员不能超过 100 人") Set<UUID> memberProfileIds,
            @Size(max = 20, message = "项目管理员不能超过 20 人") Set<UUID> administratorProfileIds
    ) {
    }
}
