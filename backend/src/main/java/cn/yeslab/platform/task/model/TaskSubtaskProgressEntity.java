package cn.yeslab.platform.task.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 某个对象对某个子任务的提交情况。
 *
 * <p>子任务不是「勾选完成」，而是**成员提交一段富文本内容**；提交即视为该子任务完成，
 * 大任务被通过前可以修改并重新提交。{@code completed} 因此表示「已提交内容」，
 * {@code completedAt} 表示提交时间。</p>
 */
@Entity
@Table(name = "task_subtask_progress")
public class TaskSubtaskProgressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private TaskAssignmentEntity assignment;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "subtask_id", nullable = false)
    private TaskSubtaskEntity subtask;

    /** 是否已提交（数据库列名保持 completed，避免动历史数据）。 */
    @Column(name = "completed", nullable = false)
    private boolean submitted;

    /** 成员为该子任务提交的富文本内容；未提交时为 null。 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String contentHtml;

    @Column(name = "completed_at")
    private Instant submittedAt;

    protected TaskSubtaskProgressEntity() {
    }

    /** 提交（或重新提交）该子任务的内容。 */
    public TaskSubtaskProgressEntity(TaskAssignmentEntity assignment, TaskSubtaskEntity subtask, String contentHtml) {
        this.assignment = assignment;
        this.subtask = subtask;
        submit(contentHtml);
    }

    public UUID getId() { return id; }
    public TaskAssignmentEntity getAssignment() { return assignment; }
    public TaskSubtaskEntity getSubtask() { return subtask; }
    public boolean isSubmitted() { return submitted; }
    public String getContentHtml() { return contentHtml; }
    public Instant getSubmittedAt() { return submittedAt; }

    /** 提交或重新提交：覆盖内容并刷新提交时间。 */
    public void submit(String contentHtml) {
        this.contentHtml = contentHtml;
        this.submitted = true;
        this.submittedAt = Instant.now();
    }
}
