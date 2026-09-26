package cn.yeslab.platform.task.model;

import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.recruitment.model.RecruitmentApplicationEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 任务对象：一条记录同时承载「发放给谁」与「完成情况」。
 *
 * <p>{@code memberProfile} 与 {@code recruitmentApplication} 恰好一个非空：
 * 普通任务挂在成员档案上，新手任务挂在招新报名记录上。</p>
 */
@Entity
@Table(name = "task_assignments")
public class TaskAssignmentEntity {

    private static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private TaskEntity task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_profile_id")
    private MemberProfileEntity memberProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_application_id")
    private RecruitmentApplicationEntity recruitmentApplication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskAssignmentSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskAssignmentStatus status = TaskAssignmentStatus.PENDING;

    /** 新手任务：本人截止日期 = 发放当天 + 大任务时长；普通任务使用任务级的起止日期。 */
    private LocalDate dueDate;

    /** 新手/普通任务最近一次驳回后的个人补交截止时间（驳回时刻 + 24 小时）；悬赏不使用。 */
    private Instant resubmissionDeadlineAt;

    /** 悬赏：完成名次（第几个完成，从 1 开始）。一经分配不再重算，作为历史留痕。 */
    private Integer completionRank;

    /**
     * 悬赏：是否持有奖金份额。
     *
     * <p>这是「奖金持有者 = 已完成对象里名次最靠前的 min(m, 已完成人数) 位」这条不变量在库里的投影列，
     * 在每次「完成」或「驳回」后重算：驳回一位持有者时，他的份额会自动顺延给名次最靠前的未获奖完成者。</p>
     */
    @Column(nullable = false)
    private boolean prizeAwarded;

    /** 最近一次「按人延长截止日期」的时间、操作人与理由（逾期后的补救动作留痕）。 */
    private Instant dueDateExtendedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "due_date_extended_by_account_id")
    private AccountEntity dueDateExtendedBy;

    @Column(length = 500)
    private String dueDateExtensionReason;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String completionNote;

    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_account_id")
    private AccountEntity reviewedBy;

    private Instant reviewedAt;

    @Column(length = 1000)
    private String reviewComment;

    @Column(length = 500)
    private String exemptionReason;

    @Column(name = "converted_profile_id")
    private UUID convertedProfileId;

    @Column(name = "point_grant_id")
    private UUID pointGrantId;

    private Integer awardedPoints;

    @Column(length = 200)
    private String pointsSkippedReason;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskSubtaskProgressEntity> subtaskProgress = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected TaskAssignmentEntity() {
    }

    public TaskAssignmentEntity(
            TaskEntity task,
            MemberProfileEntity memberProfile,
            RecruitmentApplicationEntity recruitmentApplication,
            TaskAssignmentSource source
    ) {
        this.task = task;
        this.memberProfile = memberProfile;
        this.recruitmentApplication = recruitmentApplication;
        this.source = source;
    }

    public UUID getId() { return id; }
    public TaskEntity getTask() { return task; }
    public MemberProfileEntity getMemberProfile() { return memberProfile; }
    public RecruitmentApplicationEntity getRecruitmentApplication() { return recruitmentApplication; }
    public TaskAssignmentSource getSource() { return source; }
    public TaskAssignmentStatus getStatus() { return status; }
    public LocalDate getDueDate() { return dueDate; }
    public Instant getResubmissionDeadlineAt() { return resubmissionDeadlineAt; }
    public Integer getCompletionRank() { return completionRank; }
    public boolean isPrizeAwarded() { return prizeAwarded; }
    public Instant getDueDateExtendedAt() { return dueDateExtendedAt; }
    public AccountEntity getDueDateExtendedBy() { return dueDateExtendedBy; }
    public String getDueDateExtensionReason() { return dueDateExtensionReason; }
    public String getCompletionNote() { return completionNote; }
    public Instant getSubmittedAt() { return submittedAt; }
    public AccountEntity getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public String getExemptionReason() { return exemptionReason; }
    public UUID getConvertedProfileId() { return convertedProfileId; }
    public UUID getPointGrantId() { return pointGrantId; }
    public Integer getAwardedPoints() { return awardedPoints; }
    public String getPointsSkippedReason() { return pointsSkippedReason; }
    public List<TaskSubtaskProgressEntity> getSubtaskProgress() { return List.copyOf(subtaskProgress); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isOnboarding() { return task != null && task.isOnboarding(); }

    /** 对象是否为当前成员本人。 */
    public boolean belongsTo(MemberProfileEntity profile) {
        return memberProfile != null && profile != null && memberProfile.getId().equals(profile.getId());
    }

    public Optional<TaskSubtaskProgressEntity> progressOf(UUID subtaskId) {
        return subtaskProgress.stream()
                .filter(item -> item.getSubtask() != null && item.getSubtask().getId().equals(subtaskId))
                .findFirst();
    }

    public int submittedSubtaskCount() {
        return (int) subtaskProgress.stream().filter(TaskSubtaskProgressEntity::isSubmitted).count();
    }

    /** 大任务下的子任务是否已全部提交内容：这是提交完成说明与审核通过的前置条件。 */
    public boolean hasSubmittedAllSubtasks() {
        int total = task == null ? 0 : task.getSubtasks().size();
        return total > 0 && submittedSubtaskCount() >= total;
    }

    /** 本人发放日期（按实验室时区取创建日），用于「发放当天 + 时长」计算截止日期。 */
    public LocalDate issuedOn() {
        return createdAt.atZone(LAB_TIME_ZONE).toLocalDate();
    }

    public void assignDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        this.updatedAt = Instant.now();
    }

    /**
     * 管理员按人延长截止日期。
     *
     * <p>这是新手任务逾期后恢复成员提交能力的动作：基准到期只冻结新提交，管理端仍可处理已提交结论；把 {@code due_date}
     * 推到未来之后，本人即可继续提交、管理员也才能审核转正。最近一次延长的时间、操作人与理由
     * 一并留痕。</p>
     */
    public void extendDueDate(LocalDate newDueDate, AccountEntity operator, String reason) {
        this.dueDate = newDueDate;
        this.dueDateExtendedAt = Instant.now();
        this.dueDateExtendedBy = operator;
        this.dueDateExtensionReason = reason;
        this.updatedAt = Instant.now();
    }

    /**
     * 管理员给大任务新增子任务后，把「已提交待确认」退回「待完成」，让报名者勾选新项后重新提交。
     * 已终态（已通过）的对象不退回。
     */
    public boolean reopenForNewSubtasks() {
        if (status != TaskAssignmentStatus.SUBMITTED) return false;
        this.status = TaskAssignmentStatus.PENDING;
        this.submittedAt = null;
        this.updatedAt = Instant.now();
        return true;
    }

    /** 报名者在待确认状态修改勾选内容时，同样退回待完成，避免用旧提交通过新内容。 */
    public void reopenForEdit() {
        if (status != TaskAssignmentStatus.SUBMITTED) return;
        this.status = TaskAssignmentStatus.PENDING;
        this.submittedAt = null;
        this.updatedAt = Instant.now();
    }

    public boolean isTerminal() {
        return status == TaskAssignmentStatus.APPROVED
                || status == TaskAssignmentStatus.REJECTED
                || status == TaskAssignmentStatus.ABANDONED;
    }

    /** 只有待确认状态可以被管理员通过或驳回。 */
    public boolean isReviewable() { return status == TaskAssignmentStatus.SUBMITTED; }

    public void submit(String note) {
        this.completionNote = note;
        this.submittedAt = Instant.now();
        this.status = TaskAssignmentStatus.SUBMITTED;
        this.updatedAt = Instant.now();
    }

    public void approve(AccountEntity reviewer, String comment) {
        this.status = TaskAssignmentStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewComment = comment;
        this.updatedAt = Instant.now();
    }

    public void reject(AccountEntity reviewer, String comment) {
        reject(reviewer, comment, Instant.now());
    }

    public void reject(AccountEntity reviewer, String comment, Instant reviewedAt) {
        this.status = TaskAssignmentStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = reviewedAt;
        this.reviewComment = comment;
        this.resubmissionDeadlineAt = reviewedAt.plus(java.time.Duration.ofHours(24));
        this.updatedAt = Instant.now();
    }

    /**
     * 悬赏：提交完成说明即完成。写入完成名次（历史第几个完成，只增不复用），状态直接置为已通过。
     *
     * <p>悬赏没有「待确认 → 管理员审核」环节：先完成先得，管理员只做事后复核（驳回）。</p>
     */
    public void completeWithRank(int rank, String note) {
        this.completionNote = note;
        this.submittedAt = Instant.now();
        this.completionRank = rank;
        this.status = TaskAssignmentStatus.APPROVED;
        this.updatedAt = Instant.now();
    }

    /**
     * 悬赏：本人放弃或被管理员移除。状态置为 {@link TaskAssignmentStatus#ABANDONED}，归还接取名额。
     * 保留记录是为了实现「同一人对同一悬赏只能接取一次」——本人此后再接会被服务层与唯一约束拦下。
     */
    public void abandon() {
        this.status = TaskAssignmentStatus.ABANDONED;
        this.updatedAt = Instant.now();
    }

    /**
     * 悬赏：管理员事后驳回（撤回完成）。积分不随驳回回收（更正走积分管理的反向流水），
     * 奖金份额由 {@code BountyService} 重算并顺延给下一位完成者。
     */
    public void revokeCompletion(AccountEntity reviewer, String comment) {
        this.status = TaskAssignmentStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewComment = comment;
        this.updatedAt = Instant.now();
    }

    /** 悬赏：更新是否持有奖金份额（不变量重算的结果）。 */
    public void markPrize(boolean awarded) {
        this.prizeAwarded = awarded;
        this.updatedAt = Instant.now();
    }

    public void recordPointsGrant(UUID grantId, Integer creditedPoints) {
        this.pointGrantId = grantId;
        this.awardedPoints = creditedPoints;
        this.pointsSkippedReason = null;
        this.updatedAt = Instant.now();
    }

    public void recordPointsSkipped(String reason) {
        this.pointGrantId = null;
        this.awardedPoints = null;
        this.pointsSkippedReason = reason;
        this.updatedAt = Instant.now();
    }

    public void recordConversion(UUID profileId) {
        this.convertedProfileId = profileId;
        this.updatedAt = Instant.now();
    }

    public void recordExemption(String reason) {
        this.exemptionReason = reason;
        this.updatedAt = Instant.now();
    }

    /** 成员为某个子任务提交内容：已有记录则覆盖（重新提交），否则新建。 */
    public void submitSubtaskProgress(TaskSubtaskEntity subtask, String contentHtml) {
        TaskSubtaskProgressEntity existing = progressOf(subtask.getId()).orElse(null);
        if (existing == null) {
            this.subtaskProgress.add(new TaskSubtaskProgressEntity(this, subtask, contentHtml));
        } else {
            existing.submit(contentHtml);
        }
        this.updatedAt = Instant.now();
    }
}
