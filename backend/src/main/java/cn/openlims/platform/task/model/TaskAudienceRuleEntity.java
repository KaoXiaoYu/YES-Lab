package cn.openlims.platform.task.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 等级发放条件的一行。列名使用 {@code rule_value}，因为 {@code value} 在 H2 中是保留字。
 */
@Entity
@Table(name = "task_audience_rules")
public class TaskAudienceRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private TaskEntity task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskAudienceDimension dimension;

    @Column(name = "rule_value", nullable = false, length = 120)
    private String ruleValue;

    protected TaskAudienceRuleEntity() {
    }

    public TaskAudienceRuleEntity(TaskEntity task, TaskAudienceDimension dimension, String ruleValue) {
        this.task = task;
        this.dimension = dimension;
        this.ruleValue = ruleValue;
    }

    public UUID getId() { return id; }
    public TaskEntity getTask() { return task; }
    public TaskAudienceDimension getDimension() { return dimension; }
    public String getRuleValue() { return ruleValue; }
}
