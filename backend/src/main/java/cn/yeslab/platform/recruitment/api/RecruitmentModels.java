package cn.yeslab.platform.recruitment.api;

import cn.yeslab.platform.recruitment.model.RecruitmentStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecruitmentModels {

    private RecruitmentModels() {
    }

    public record ApplicationRequest(
            @NotBlank(message = "请输入姓名") @Size(max = 80) String name,
            @NotBlank(message = "请输入专业") @Size(max = 100) String major,
            @NotBlank(message = "请输入班级") @Size(max = 100) String className,
            @Size(max = 30) String grade,
            @NotBlank(message = "请输入联系方式") @Size(max = 200) String contact,
            @NotEmpty(message = "请至少选择一个兴趣方向") List<@NotBlank @Size(max = 80) String> interestDirections,
            @NotNull List<@NotBlank @Size(max = 100) String> existingSkills,
            @Size(max = 5000, message = "项目或竞赛经历不能超过 5000 个字符") String experience,
            @NotEmpty(message = "请至少选择一个意向标签") List<@NotBlank @Size(max = 80) String> intendedTags
    ) {
    }

    public record StageChangeRequest(
            @NotNull RecruitmentStage stage,
            @Size(max = 500) String note,
            @Size(max = 100) String linkedQuizId
    ) {
    }

    public record InterviewRequest(
            @NotBlank(message = "请选择面试官") String interviewerUsername,
            @Min(value = 0, message = "评分不能小于 0") @Max(value = 100, message = "评分不能大于 100") Integer score,
            @Size(max = 5000) String evaluation,
            @NotNull List<@NotBlank @Size(max = 80) String> suggestedTags,
            Boolean passed
    ) {
    }

    public record ConvertMemberRequest(
            @NotBlank(message = "请输入学号或内部编号") @Size(max = 64) String memberCode,
            @NotEmpty(message = "正式成员至少需要一个能力标签") List<@NotBlank @Size(max = 80) String> skillTags
    ) {
    }

    public record HistoryView(
            RecruitmentStage fromStage,
            RecruitmentStage toStage,
            String operatorUsername,
            String note,
            Instant changedAt
    ) {
    }

    public record InterviewView(
            UUID interviewerAccountId,
            String interviewerName,
            Integer score,
            String evaluation,
            List<String> suggestedTags,
            Boolean passed
    ) {
    }

    public record ApplicationView(
            UUID id,
            UUID applicantAccountId,
            String applicantUsername,
            String name,
            String major,
            String className,
            String grade,
            String contact,
            List<String> interestDirections,
            List<String> existingSkills,
            String experience,
            List<String> intendedTags,
            RecruitmentStage stage,
            InterviewView interview,
            String linkedQuizId,
            UUID convertedMemberId,
            Instant createdAt,
            Instant updatedAt,
            List<HistoryView> history
    ) {
    }

    public record InterviewerView(UUID accountId, String username, String role) {
    }
}
