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

    public int completedSubtaskCount() {
        return (int) subtaskProgress.stream().filter(TaskSubtaskProgressEntity::isCompleted).count();
    }

    /** 大任务下的子任务是否已全部勾选：这是提交完成说明与审核通过的前置条件。 */
    public boolean hasCompletedAllSubtasks() {
        int total = task == null ? 0 : task.getSubtasks().size();
        return total > 0 && completedSubtaskCount() >= total;
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
        return status == TaskAssignmentStatus.APPROVED || status == TaskAssignmentStatus.REJECTED;
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
        this.status = TaskAssignmentStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewComment = comment;
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

    public void upsertProgress(TaskSubtaskEntity subtask, boolean completed) {
        TaskSubtaskProgressEntity existing = progressOf(subtask.getId()).orElse(null);
        if (existing == null) {
            this.subtaskProgress.add(new TaskSubtaskProgressEntity(this, subtask, completed));
        } else {
            existing.mark(completed);
        }
        this.updatedAt = Instant.now();
    }
}
