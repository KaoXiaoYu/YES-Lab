package cn.yeslab.platform.task.api;

import cn.yeslab.platform.recruitment.model.RecruitmentStage;
import cn.yeslab.platform.task.model.TaskAssignmentStatus;
import cn.yeslab.platform.task.model.TaskAudienceDimension;
import cn.yeslab.platform.task.model.TaskStatus;
import cn.yeslab.platform.task.model.TaskType;
import cn.yeslab.platform.task.model.BountyPrizeFulfillmentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TaskModels {

    private TaskModels() {
    }

    /** 人工审核结论：普通任务通过时计分，新手任务通过时转正。 */
    public enum ReviewDecision {
        APPROVED,
        REJECTED
    }

    // ---------- 通用 ----------

    public record SubtaskView(
            UUID id,
            String title,
            int displayOrder,
            /** 本人是否已提交该子任务的内容（子任务不是勾选完成，而是提交一段内容）。 */
            boolean submitted,
            Instant submittedAt,
            /** 该子任务是否有管理员写的富文本说明；列表与进度接口不返回说明正文本身。 */
            boolean hasContent
    ) {
    }

    /** 子任务写入项：{@code id} 为空表示新增，缺失的 {@code id} 表示删除，{@code contentHtml} 为空表示只有标题。 */
    public record SubtaskInput(
            UUID id,
            @NotBlank(message = "请填写子任务标题") @Size(max = 200, message = "子任务标题不能超过 200 个字符") String title,
            @Size(max = 5000, message = "子任务说明不能超过 5000 个字符") String contentHtml
    ) {
    }

    /** 子任务详情：正文 + 自己的完成状态 + 大任务上下文（懒加载，只有详情接口返回正文）。 */
    public record SubtaskDetailView(
            UUID subtaskId,
            UUID taskId,
            UUID assignmentId,
            String taskTitle,
            TaskType taskType,
            String title,
            String contentHtml,
            boolean submitted,
            Instant submittedAt,
            /** 本人已提交的子任务内容，未提交时为 null。 */
            String submittedContentHtml,
            int submittedSubtasks,
            int totalSubtasks,
            LocalDate dueDate,
            boolean overdue,
            boolean editable
    ) {
    }

    // ---------- 新手任务（报名者端） ----------

    public record OnboardingTaskView(
            UUID assignmentId,
            UUID taskId,
            String title,
            String contentHtml,
            LocalDate startDate,
            LocalDate endDate,
            Instant resubmissionDeadlineAt,
            long daysRemaining,
            boolean overdue,
            TaskAssignmentStatus status,
            String completionNote,
            Instant submittedAt,
            String reviewComment,
            String exemptionReason,
            UUID convertedProfileId,
            int submittedSubtasks,
            int totalSubtasks,
            boolean allSubtasksSubmitted,
            boolean editable,
            List<SubtaskView> subtasks
    ) {
    }

    public record SubmitTaskRequest(@NotBlank(message = "请填写完成说明") String completionNote) {
    }

    /** 成员为某个子任务提交（或重新提交）富文本内容。 */
    public record SubtaskSubmissionRequest(
            @NotBlank(message = "请填写该子任务的完成内容") @Size(max = 5000, message = "提交内容不能超过 5000 个字符") String contentHtml
    ) {
    }

    /** 管理端逐条查看某人的子任务提交内容（懒加载，只在展开时请求）。 */
    public record SubtaskSubmissionView(
            UUID subtaskId,
            String title,
            String subtaskContentHtml,
            boolean submitted,
            Instant submittedAt,
            String contentHtml
    ) {
    }

    // ---------- 新手任务大任务（管理端） ----------

    /** 子任务定义（大任务自身持有的清单，与「某人是否已勾选」无关）；正文在编辑时按需单独读取。 */
    public record SubtaskDefinitionView(UUID id, String title, int displayOrder, boolean hasContent) {
    }

    public record OnboardingTaskAdminView(
            UUID taskId,
            String title,
            String contentHtml,
            int durationDays,
            List<SubtaskDefinitionView> subtasks,
            Instant updatedAt,
            boolean usingBuiltInDefault
    ) {
    }

    public record SaveOnboardingTaskRequest(
            @NotBlank(message = "请输入任务标题") @Size(max = 160, message = "标题不能超过 160 个字符") String title,
            @NotBlank(message = "请填写新手任务说明") String contentHtml,
            @Min(value = 1, message = "时长至少为 1 天") @Max(value = 365, message = "时长不能超过 365 天") int durationDays,
            @NotEmpty(message = "请至少添加一项子任务") @Size(max = 50, message = "子任务不能超过 50 项")
            @Valid List<SubtaskInput> subtasks
    ) {
    }

    /** 保存结果：reopenedCount = 因新增子任务被退回「待完成」的人数；rescheduledCount = 截止日期被重算的人数。 */
    public record SaveOnboardingTaskResult(
            OnboardingTaskAdminView task,
            int reopenedCount,
            int rescheduledCount
    ) {
    }

    public record BackfillResult(int issued, int skipped) {
    }

    /** 按人延长新手任务截止日期：新日期必须晚于今天，且晚于原日期。 */
    public record ExtendOnboardingDueDateRequest(
            @NotNull(message = "请选择新的截止日期") LocalDate dueDate,
            @Size(max = 500, message = "延长理由不能超过 500 字") String reason
    ) {
    }

    /** 延长结果：新的截止日期与本次留痕。 */
    public record DueDateExtensionView(
            UUID assignmentId,
            UUID applicationId,
            String applicantName,
            LocalDate previousDueDate,
            LocalDate dueDate,
            Instant extendedAt,
            String extendedBy,
            String reason
    ) {
    }

    public record OnboardingOverviewView(
            OnboardingTaskAdminView task,
            int skillTestCount,
            int missingTaskCount,
            List<OnboardingRowView> rows
    ) {
    }

    // ---------- 管理端：新手任务完成情况 ----------

    public record OnboardingRowView(
            UUID assignmentId,
            UUID taskId,
            UUID applicationId,
            String applicantName,
            String applicantUsername,
            String memberCode,
            List<String> skillTags,
            RecruitmentStage stage,
            TaskAssignmentStatus status,
            int submittedSubtasks,
            int totalSubtasks,
            String completionNote,
            Instant submittedAt,
            String reviewedBy,
            Instant reviewedAt,
            String reviewComment,
            String exemptionReason,
            UUID convertedProfileId,
            LocalDate startDate,
            LocalDate endDate,
            boolean overdue,
            Instant resubmissionDeadlineAt,
            Instant dueDateExtendedAt,
            String dueDateExtendedBy,
            String dueDateExtensionReason
    ) {
    }

    // ---------- 人工审核 ----------

    public record ReviewRequest(
            @NotNull(message = "请选择审核结论") ReviewDecision decision,
            @Size(max = 1000, message = "审核意见不能超过 1000 个字符") String comment,
            @Size(max = 1000, message = "凭证链接不能超过 1000 个字符") String evidenceUrl,
            @Size(max = 500, message = "豁免理由不能超过 500 个字符") String exemptionReason
    ) {
    }

    /** 人工审核的统一返回：新手任务与普通任务共用，按 taskType 区分含义。 */
    public record ReviewResultView(
            UUID assignmentId,
            UUID taskId,
            TaskType taskType,
            TaskAssignmentStatus status,
            String completionNote,
            Instant submittedAt,
            String reviewComment,
            String exemptionReason,
            UUID convertedProfileId,
            Integer awardedPoints,
            String pointsSkippedReason,
            LocalDate startDate,
            LocalDate endDate,
            long daysRemaining,
            boolean overdue,
            Instant resubmissionDeadlineAt,
            List<SubtaskView> subtasks
    ) {
    }

    // ---------- 普通任务 ----------

    public record AudienceRuleRequest(@NotNull TaskAudienceDimension dimension, @NotBlank @Size(max = 120) String value) {
    }

    /** 发放条件与指定成员的组合，用于发布前预览与补充发放。 */
    public record AudienceRequest(
            @Valid @Size(max = 200, message = "发放条件不能超过 200 条") List<AudienceRuleRequest> rules,
            @Size(max = 100, message = "指定成员不能超过 100 人") List<UUID> memberProfileIds
    ) {
    }

    public record CreateTaskRequest(
            @NotBlank(message = "请输入任务标题") @Size(max = 160) String title,
            @NotBlank(message = "请填写任务正文") String contentHtml,
            LocalDate startDate,
            LocalDate endDate,
            @Min(value = 0, message = "积分不能为负") @Max(value = 100000, message = "积分不能超过 100000") int points,
            @Size(max = 50, message = "子任务不能超过 50 项") @Valid List<SubtaskInput> subtasks,
            @Valid @Size(max = 200, message = "发放条件不能超过 200 条") List<AudienceRuleRequest> rules,
            @Size(max = 100, message = "指定成员不能超过 100 人") List<UUID> memberProfileIds
    ) {
    }

    /** 可指定的成员选项，供管理端选择发放对象。 */
    public record MemberOptionView(
            UUID profileId,
            String name,
            String memberCode,
            String grade,
            String memberStatus,
            String role
    ) {
    }

    public record TaskView(
            UUID id,
            TaskType taskType,
            String title,
            String contentHtml,
            LocalDate startDate,
            LocalDate endDate,
            int points,
            TaskStatus status,
            Instant publishedAt,
            int assignmentCount,
            Instant pointsSettledAt,
            boolean expired,
            List<SubtaskView> subtasks,
            List<AudienceRuleRequest> rules
    ) {
    }

    public record TaskSummaryView(
            UUID id,
            TaskType taskType,
            String title,
            int points,
            TaskStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int assignmentCount,
            int approvedCount,
            int submittedCount,
            int pendingCount,
            int rejectedCount,
            Instant pointsSettledAt,
            boolean expired
    ) {
    }

    public record AudienceMemberView(
            UUID memberProfileId,
            String name,
            String memberCode,
            String grade,
            String memberStatus,
            String role,
            String source,
            boolean pointEligible,
            String pointIneligibleReason
    ) {
    }

    public record AudiencePreviewView(
            int total,
            int pointEligibleCount,
            boolean pointsConfigured,
            List<AudienceMemberView> members
    ) {
    }

    public record AssignmentRowView(
            UUID assignmentId,
            UUID memberProfileId,
            String name,
            String memberCode,
            String grade,
            String memberStatus,
            String role,
            String source,
            TaskAssignmentStatus status,
            int submittedSubtasks,
            int totalSubtasks,
            String completionNote,
            Instant submittedAt,
            String reviewedBy,
            Instant reviewedAt,
            String reviewComment,
            Integer awardedPoints,
            String pointsSkippedReason,
            boolean overdue
    ) {
    }

    public record SubtaskProgressView(UUID subtaskId, String title, int submittedCount, int totalCount) {
    }

    public record TaskProgressView(
            TaskView task,
            int assignmentCount,
            int approvedCount,
            int submittedCount,
            int pendingCount,
            int rejectedCount,
            int awardedPointsTotal,
            List<SubtaskProgressView> subtaskProgress,
            List<AssignmentRowView> assignments
    ) {
    }

    /** 成员端「我的任务」列表项。 */
    public record MyTaskView(
            UUID assignmentId,
            UUID taskId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            int points,
            TaskStatus taskStatus,
            TaskAssignmentStatus status,
            int submittedSubtasks,
            int totalSubtasks,
            Integer awardedPoints,
            String pointsSkippedReason,
            String reviewComment,
            boolean overdue,
            boolean expired,
            boolean editable,
            Instant resubmissionDeadlineAt,
            boolean pointsSettled,
            TaskType taskType,
            String prizeDescription,
            Integer prizeSlots,
            Integer completionRank,
            boolean prizeAwarded,
            BountyPrizeFulfillmentStatus prizeFulfillmentStatus,
            Instant prizeIssuedAt,
            Instant prizeReceivedAt
    ) {
    }

    public record MyTaskDetailView(
            UUID assignmentId,
            UUID taskId,
            String title,
            String contentHtml,
            LocalDate startDate,
            LocalDate endDate,
            int points,
            TaskStatus taskStatus,
            TaskAssignmentStatus status,
            String completionNote,
            Instant submittedAt,
            String reviewComment,
            Integer awardedPoints,
            String pointsSkippedReason,
            boolean overdue,
            boolean expired,
            boolean editable,
            Instant resubmissionDeadlineAt,
            boolean pointsSettled,
            TaskType taskType,
            String prizeDescription,
            Integer prizeSlots,
            Integer completionRank,
            boolean prizeAwarded,
            BountyPrizeFulfillmentStatus prizeFulfillmentStatus,
            Instant prizeIssuedAt,
            Instant prizeReceivedAt,
            List<SubtaskView> subtasks
    ) {
    }

    // ---------- 悬赏：成员端 ----------

    /** 悬赏榜与详情共用的奖励与名额信息。 */
    public record BountyPrizeView(
            String prizeDescription,
            Integer prizeSlots,
            long prizeIssued,
            Integer headcountLimit,
            long claimed,
            int points,
            boolean pointsSettled
    ) {
    }

    public record BountyBoardItemView(
            UUID taskId,
            String title,
            String summary,
            BountyPrizeView prize,
            LocalDate startDate,
            LocalDate endDate,
            long daysRemaining,
            boolean windowOpen,
            String windowClosedReason,
            UUID myAssignmentId,
            TaskAssignmentStatus myStatus,
            Integer myRank,
            boolean myPrizeAwarded,
            boolean claimable,
            String claimBlockedReason
    ) {
    }

    public record BountyDetailView(
            UUID taskId,
            String title,
            String contentHtml,
            BountyPrizeView prize,
            LocalDate startDate,
            LocalDate endDate,
            long daysRemaining,
            boolean windowOpen,
            String windowClosedReason,
            UUID myAssignmentId,
            TaskAssignmentStatus myStatus,
            Integer myRank,
            boolean myPrizeAwarded,
            boolean claimable,
            String claimBlockedReason,
            List<SubtaskView> subtasks
    ) {
    }

    public record BountyClaimResult(
            UUID assignmentId,
            TaskAssignmentStatus status,
            long claimed,
            Integer headcountLimit
    ) {
    }

    public record BountyAbandonResult(long claimed, Integer headcountLimit) {
    }

    // ---------- 悬赏：管理端 ----------

    /**
     * 创建 / 修改悬赏。
     *
     * <p>奖金与积分互相独立、至少要有一个：只填 {@code prizeSlots}（需配 {@code prizeDescription}）
     * 或只填 {@code points} 都可以，两者都为空则拒绝。{@code headcountLimit} 为空表示不限接取人数。</p>
     */
    public record CreateBountyRequest(
            @NotBlank(message = "请输入悬赏标题") @Size(max = 160) String title,
            @NotBlank(message = "请填写悬赏正文") String contentHtml,
            @Size(max = 500, message = "奖金说明不能超过 500 字") String prizeDescription,
            @Min(value = 1, message = "奖金份数至少 1 份") @Max(value = 100, message = "奖金份数不能超过 100") Integer prizeSlots,
            @Min(value = 0, message = "积分不能为负") @Max(value = 100000, message = "积分不能超过 100000") int points,
            @Min(value = 1, message = "接取人数上限至少 1 人") @Max(value = 1000, message = "接取人数上限不能超过 1000") Integer headcountLimit,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 50, message = "子任务不能超过 50 项") @Valid List<SubtaskInput> subtasks,
            @Valid @Size(max = 200, message = "接取条件不能超过 200 条") List<AudienceRuleRequest> rules
    ) {
    }

    /** 悬赏事后驳回：只收一条必填意见。 */
    public record BountyRevokeRequest(
            @NotBlank(message = "驳回必须填写审核意见") @Size(max = 1000, message = "审核意见不能超过 1000 个字符") String comment
    ) {
    }

    public record BountyClaimRowView(
            UUID assignmentId,
            UUID memberProfileId,
            String name,
            String memberCode,
            String grade,
            String memberStatus,
            String role,
            TaskAssignmentStatus status,
            Integer completionRank,
            boolean prizeAwarded,
            String completionNote,
            Instant claimedAt,
            Instant submittedAt,
            String reviewedBy,
            Instant reviewedAt,
            String reviewComment,
            Integer awardedPoints,
            String pointsSkippedReason,
            boolean overdue,
            BountyPrizeFulfillmentStatus prizeFulfillmentStatus,
            Instant prizeIssuedAt,
            String prizeIssuedBy,
            Instant prizeReceivedAt,
            String prizeReceivedBy,
            Instant prizeRevokedAt,
            String prizeRevokedBy,
            String prizeRevokedReason
    ) {
    }

    /** 悬赏汇总：名额占用、奖金份数进度与逐人明细。 */
    public record BountyClaimsView(
            TaskView task,
            Integer headcountLimit,
            long claimed,
            long occupied,
            long approved,
            long abandoned,
            long rejected,
            Integer prizeSlots,
            long prizeIssued,
            List<BountyClaimRowView> rows
    ) {
    }

    public record BountySummaryView(
            UUID id,
            String title,
            TaskStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Integer headcountLimit,
            long claimed,
            long approved,
            Integer prizeSlots,
            /** 管理端编辑表单要回填现有奖金说明：{@code TaskView} 不含悬赏字段，只能从列表带回来。 */
            String prizeDescription,
            long prizeIssued,
            int points,
            Instant pointsSettledAt,
            boolean expired
    ) {
    }
}
