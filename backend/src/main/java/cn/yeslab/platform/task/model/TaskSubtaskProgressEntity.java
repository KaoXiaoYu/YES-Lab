package cn.yeslab.platform.task.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 某个对象对某个子任务的勾选情况。 */
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

    @Column(nullable = false)
    private boolean completed;

    private Instant completedAt;

    protected TaskSubtaskProgressEntity() {
    }

    public TaskSubtaskProgressEntity(TaskAssignmentEntity assignment, TaskSubtaskEntity subtask, boolean completed) {
        this.assignment = assignment;
        this.subtask = subtask;
        mark(completed);
    }

    public UUID getId() { return id; }
    public TaskAssignmentEntity getAssignment() { return assignment; }
    public TaskSubtaskEntity getSubtask() { return subtask; }
    public boolean isCompleted() { return completed; }
    public Instant getCompletedAt() { return completedAt; }

    public void mark(boolean value) {
        this.completed = value;
        this.completedAt = value ? Instant.now() : null;
    }
}
