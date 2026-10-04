package cn.openlims.platform.task.model;

import cn.openlims.platform.identity.model.AccountEntity;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "tasks")
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskType taskType;

    @Column(nullable = false, length = 160)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String contentHtml;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private int points;

    /** 新手任务大任务的时长（天）：每人的截止日期 = 本人发放当天 + 时长。普通任务为空。 */
    private Integer durationDays;

    /** 悬赏：接取人数上限。{@code null} = 不限人数；非空 = 最多 n 人接取、先到先得。 */
    private Integer headcountLimit;

    /** 悬赏：奖金份数 m，最先完成的 m 人获奖。{@code null} = 不设奖金。 */
    private Integer prizeSlots;

    /** 悬赏：奖金的一段统一说明（线下发放，系统只登记，不逐人区分金额）。 */
    @Column(length = 500)
    private String prizeDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    private Instant publishedAt;

    /**
     * 积分结算时间：{@code null} 表示未结算。
     *
     * <p>它同时承担两个职责：既是「已结算」的唯一标记，也是结算认领的争用字段
     * （条件更新 {@code WHERE points_settled_at IS NULL} 保证跨实例只执行一次）。
     * 到期结算只给状态为「已通过」的对象发放积分，因此结算一旦执行即为终局。</p>
     */
    private Instant pointsSettledAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_account_id", nullable = false, updatable = false)
    private AccountEntity createdBy;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<TaskSubtaskEntity> subtasks = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskAudienceRuleEntity> audienceRules = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskAssignmentEntity> assignments = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected TaskEntity() {
    }

    public TaskEntity(
            TaskType taskType,
            String title,
            String contentHtml,
            LocalDate startDate,
            LocalDate endDate,
            int points,
            TaskStatus status,
            AccountEntity createdBy
    ) {
        this.taskType = taskType;
        this.title = title;
        this.contentHtml = contentHtml;
        this.startDate = startDate;
        this.endDate = endDate;
        this.points = points;
        this.status = status;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public TaskType getTaskType() { return taskType; }
    public String getTitle() { return title; }
    public String getContentHtml() { return contentHtml; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getPoints() { return points; }
    public TaskStatus getStatus() { return status; }
    public Integer getDurationDays() { return durationDays; }
    public Integer getHeadcountLimit() { return headcountLimit; }
    public Integer getPrizeSlots() { return prizeSlots; }
    public String getPrizeDescription() { return prizeDescription; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getPointsSettledAt() { return pointsSettledAt; }
    public AccountEntity getCreatedBy() { return createdBy; }
    public List<TaskSubtaskEntity> getSubtasks() { return List.copyOf(subtasks); }
    public List<TaskAudienceRuleEntity> getAudienceRules() { return List.copyOf(audienceRules); }
    public List<TaskAssignmentEntity> getAssignments() { return List.copyOf(assignments); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isOnboarding() { return taskType == TaskType.ONBOARDING; }

    public boolean isBounty() { return taskType == TaskType.BOUNTY; }

    /** 是否设了奖金份数。 */
    public boolean hasPrize() { return prizeSlots != null && prizeSlots > 0; }

    /** 接取人数上限；{@code null} 表示不限。 */
    public boolean hasHeadcountLimit() { return headcountLimit != null; }

    /** 悬赏内容写入：标题、正文、奖金说明与份数、接取上限、起止日期。 */
    public void updateBountyDetails(
            String title,
            String contentHtml,
            String prizeDescription,
            Integer prizeSlots,
            Integer headcountLimit,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.title = title;
        this.contentHtml = contentHtml;
        this.prizeDescription = prizeDescription;
        this.prizeSlots = prizeSlots;
        this.headcountLimit = headcountLimit;
        this.startDate = startDate;
        this.endDate = endDate;
        this.updatedAt = Instant.now();
    }

    /** 上调接取名额上限（发布后只增不减）。 */
    public void raiseHeadcountLimit(int newLimit) {
        this.headcountLimit = newLimit;
        this.updatedAt = Instant.now();
    }

    /** 上调奖金份数（发布后只增不减）。 */
    public void raisePrizeSlots(int newSlots) {
        this.prizeSlots = newSlots;
        this.updatedAt = Instant.now();
    }

    /** 是否已结算积分。 */
    public boolean isPointsSettled() { return pointsSettledAt != null; }

    /** 是否绑定了积分（新手任务恒为 0，因此不参与结算）。 */
    public boolean hasBoundPoints() { return points > 0; }

    public void updateDetails(
            String title,
            String contentHtml,
            LocalDate startDate,
            LocalDate endDate,
            int points
    ) {
        this.title = title;
        this.contentHtml = contentHtml;
        this.startDate = startDate;
        this.endDate = endDate;
        this.points = points;
        this.updatedAt = Instant.now();
    }

    /** 更新内容但保留分值：已发布任务的分值在发布时锁定，不随内容修改而变动。 */
    public void updateContentOnly(String title, String contentHtml, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.contentHtml = contentHtml;
        this.startDate = startDate;
        this.endDate = endDate;
        this.updatedAt = Instant.now();
    }

    /** 标记积分已结算。由结算服务的条件更新认领之后调用。 */
    public void markPointsSettled(Instant settledAt) {
        this.pointsSettledAt = settledAt;
        this.updatedAt = Instant.now();
    }

    /**
     * 重置结算标记，让任务重新进入待结算队列。
     *
     * <p>只在「延长截止日期」时使用：已结算的任务如果被延后，新的完成者否则将永远拿不到积分。
     * 已发放过的对象靠来源编号幂等跳过，因此重复结算不会重复计分。</p>
     */
    public void resetPointsSettlement() {
        this.pointsSettledAt = null;
        this.updatedAt = Instant.now();
    }

    /** 子任务写入草稿：{@code id} 为空表示新增子任务。 */
    public record SubtaskDraft(UUID id, String title, String contentHtml) {
    }

    /** 新建或草稿状态的任务：按提交顺序整体重建子任务清单（无历史勾选需要保留）。 */
    public void replaceSubtasks(List<SubtaskDraft> drafts) {
        this.subtasks.clear();
        int order = 0;
        for (SubtaskDraft draft : drafts) {
            this.subtasks.add(new TaskSubtaskEntity(this, draft.title(), draft.contentHtml(), order++));
        }
        this.updatedAt = Instant.now();
    }

    /**
     * 按 {@code id} 同步子任务（不再按标题匹配，避免改标题时丢正文与勾选记录）：
     * 带 {@code id} 的更新标题与正文并保留勾选记录、不带 {@code id} 的新增、未出现的删除，最后按提交顺序重排。
     *
     * <p>调用方需要在调用本方法之前先删除「将被移除的子任务」对应的勾选记录，
     * 否则子任务与勾选记录之间的外键会阻止删除。</p>
     *
     * @return 本次新增的子任务标题，供「新增子任务 → 退回待确认对象」的规则使用
     */
    public List<String> syncSubtasks(List<SubtaskDraft> drafts) {
        Map<UUID, TaskSubtaskEntity> existing = new LinkedHashMap<>();
        for (TaskSubtaskEntity item : subtasks) {
            existing.put(item.getId(), item);
        }
        Set<UUID> keep = drafts.stream()
                .map(SubtaskDraft::id)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        this.subtasks.removeIf(item -> !keep.contains(item.getId()));

        List<String> added = new ArrayList<>();
        List<TaskSubtaskEntity> ordered = new ArrayList<>();
        int order = 0;
        for (SubtaskDraft draft : drafts) {
            TaskSubtaskEntity item = draft.id() == null ? null : existing.get(draft.id());
            if (item == null || !this.subtasks.contains(item)) {
                item = new TaskSubtaskEntity(this, draft.title(), draft.contentHtml(), order);
                added.add(draft.title());
            } else {
                item.rename(draft.title());
                item.updateContent(draft.contentHtml());
                item.reorder(order);
            }
            ordered.add(item);
            order++;
        }
        this.subtasks.clear();
        this.subtasks.addAll(ordered);
        this.updatedAt = Instant.now();
        return added;
    }

    public void replaceAudienceRules(List<TaskAudienceRuleEntity> rules) {
        this.audienceRules.clear();
        this.audienceRules.addAll(rules);
        this.updatedAt = Instant.now();
    }

    public void markPublished() {
        this.status = TaskStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markClosed() {
        this.status = TaskStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    /** 新手任务大任务：内容、时长即时对所有在途对象生效（截止日期按每人的发放当天重算）。 */
    public void updateOnboardingDetails(String title, String contentHtml, int durationDays) {
        this.title = title;
        this.contentHtml = contentHtml;
        this.durationDays = durationDays;
        this.updatedAt = Instant.now();
    }

    /**
     * 通过聚合集合维护任务对象：集合为 cascade=ALL + orphanRemoval，
     * 因此新增与移除都要走集合，否则当前事务内的视图会读到过期的对象列表。
     */
    public void addAssignment(TaskAssignmentEntity assignment) {
        this.assignments.add(assignment);
        this.updatedAt = Instant.now();
    }

    public void removeAssignment(TaskAssignmentEntity assignment) {
        this.assignments.remove(assignment);
        this.updatedAt = Instant.now();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
