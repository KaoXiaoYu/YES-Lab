package cn.yeslab.platform.recruitment.api;

import cn.yeslab.platform.recruitment.model.RecruitmentStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
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
            @Size(max = 200) String contact,
            @NotBlank(message = "请输入邮箱") @Email(message = "请输入有效的邮箱") @Size(max = 190) String email,
            @Size(max = 30) @Pattern(regexp = "^$|\\+?[1-9][0-9 -]{6,24}", message = "请输入有效的手机号码") String phone,
            @Size(max = 80) String wechat,
            @Size(max = 5000, message = "自我介绍不能超过 5000 个字符") String selfIntroduction,
            @NotEmpty(message = "请至少选择一个兴趣方向") List<@NotBlank @Size(max = 80) String> interestDirections,
            @NotNull List<@NotBlank @Size(max = 100) String> existingSkills,
            @Size(max = 5000, message = "项目或竞赛经历不能超过 5000 个字符") String experience,
            @NotEmpty(message = "请至少选择一个意向标签") List<@NotBlank @Size(max = 80) String> intendedTags,
            @Size(max = 3000, message = "个人展示介绍不能超过 3000 个字符") String portfolioIntroduction,
            @NotNull @Size(max = 8, message = "个人链接最多填写 8 个") List<@NotNull MediaLinkRequest> mediaLinks,
            @NotNull @Size(min = 5, max = 5, message = "技术认知题目必须为 5 道") List<@NotBlank String> technicalQuestionIds,
            @NotNull @Size(min = 3, max = 5, message = "请选择至少 3 道技术认知题作答") List<@NotNull TechnicalAnswerRequest> technicalAnswers
    ) {
    }

    public record MediaLinkRequest(
            @NotBlank @Size(max = 40) String platform,
            @Size(max = 100) String account,
            @NotBlank @Size(max = 500) String url
    ) { }

    public record TechnicalAnswerRequest(
            @NotBlank String questionId,
            @NotBlank(message = "技术认知回答不能为空") @Size(max = 2000) String answer
    ) { }

    public record TechnicalQuestionView(String id, String prompt) { }

    public record PortfolioImageView(
            UUID id, String originalName, String contentType, long sizeBytes, int displayOrder, String url
    ) { }

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
            String email, String phone, String wechat, String selfIntroduction,
            List<String> interestDirections,
            List<String> existingSkills,
            String experience,
            List<String> intendedTags,
            String portfolioIntroduction,
            List<MediaLinkRequest> mediaLinks,
            List<TechnicalQuestionView> technicalQuestions,
            List<TechnicalAnswerRequest> technicalAnswers,
            List<PortfolioImageView> portfolioImages,
            RecruitmentStage stage,
            InterviewView interview,
            String linkedQuizId,
            UUID convertedMemberId,
            Instant createdAt,
            Instant updatedAt,
            List<HistoryView> history
    ) {
    }

    public record InterviewerView(UUID accountId, String username, String name, String memberCode, String role) {
    }
}
