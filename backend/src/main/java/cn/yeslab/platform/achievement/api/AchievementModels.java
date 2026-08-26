package cn.yeslab.platform.achievement.api;

import cn.yeslab.platform.achievement.model.CompetitionLevel;
import cn.yeslab.platform.achievement.model.CompetitionLifecycle;
import cn.yeslab.platform.achievement.model.VerificationStatus;
import cn.yeslab.platform.identity.model.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AchievementModels {
    private AchievementModels() {}

    public record MemberOption(UUID id, String name, Role role, String avatarUrl) {}
    public record ParticipantRequest(@NotBlank @Size(max = 80) String displayName, UUID linkedProfileId) {}
    public record ParticipantView(String displayName, UUID linkedProfileId, String avatarUrl, boolean captain) {}
    public record ImageView(UUID id, String url, String description, int displayOrder) {}
    public record ProjectOption(UUID id, String name, String teamName) {}

    public record CompetitionUpsertRequest(
            @NotBlank(message = "请输入比赛名称") @Size(max = 180) String name,
            @Size(max = 180) String track,
            @NotNull(message = "请选择比赛级别") CompetitionLevel level,
            @NotNull(message = "请选择比赛状态") CompetitionLifecycle lifecycle,
            @Size(max = 160) String awardName,
            @NotBlank(message = "请输入比赛描述") @Size(max = 10000) String description,
            LocalDate competitionDate,
            LocalDate provincialDate,
            LocalDate nationalDate,
            UUID advisorProfileId,
            @Size(max = 80) String advisorName,
            UUID projectId,
            @Size(max = 50, message = "参赛成员不能超过 50 人") List<@Valid ParticipantRequest> participants,
            @Size(max = 8, message = "比赛图片不能超过 8 张") List<@Size(max = 300) String> imageDescriptions
    ) {}

    public record CompetitionView(
            UUID id, String name, String track, CompetitionLevel level, CompetitionLifecycle lifecycle,
            String awardName, String description, LocalDate competitionDate, LocalDate provincialDate,
            LocalDate nationalDate, MemberOption captain, MemberOption advisor, String advisorName,
            ProjectOption project, List<ParticipantView> participants, List<ImageView> images,
            VerificationStatus verificationStatus, String reviewNote, String reviewerName, Instant reviewedAt,
            boolean featured, int displayOrder, boolean hasCertificate, String certificateOriginalName,
            boolean canEdit, boolean canReview, Instant createdAt, Instant updatedAt
    ) {}

    public record PublicCompetitionView(
            UUID id, String name, String track, CompetitionLevel level, String awardName, String description,
            LocalDate competitionDate, MemberOption captain, MemberOption advisor, String advisorName,
            ProjectOption project, List<ParticipantView> participants, List<ImageView> images,
            int displayOrder, Instant updatedAt
    ) {}

    public record ReviewRequest(
            @NotNull VerificationStatus status,
            @Size(max = 500) String note
    ) {}

    public record DisplayRequest(
            boolean featured,
            @Min(0) @Max(9999) int displayOrder
    ) {}

    public record NewsRequest(
            @NotBlank @Size(max = 220) String title,
            @NotBlank @Size(max = 120) String sourceName,
            @NotBlank @Size(max = 800) String sourceUrl,
            @NotBlank @Size(max = 5000) String summary,
            @NotNull LocalDate publishedDate,
            boolean visible
    ) {}

    public record NewsView(
            UUID id, String title, String sourceName, String sourceUrl, String summary,
            LocalDate publishedDate, boolean visible, Instant createdAt, Instant updatedAt
    ) {}
}
