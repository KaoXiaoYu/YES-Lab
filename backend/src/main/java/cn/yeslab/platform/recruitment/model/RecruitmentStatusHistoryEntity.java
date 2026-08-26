package cn.yeslab.platform.recruitment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recruitment_status_history")
public class RecruitmentStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RecruitmentStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecruitmentStage toStage;

    @Column(nullable = false)
    private UUID operatorAccountId;

    @Column(nullable = false, length = 64)
    private String operatorUsername;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant changedAt = Instant.now();

    protected RecruitmentStatusHistoryEntity() {
    }

    public RecruitmentStatusHistoryEntity(
            UUID applicationId,
            RecruitmentStage fromStage,
            RecruitmentStage toStage,
            AccountEntitySnapshot operator,
            String note
    ) {
        this.applicationId = applicationId;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.operatorAccountId = operator.id();
        this.operatorUsername = operator.username();
        this.note = note;
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public RecruitmentStage getFromStage() { return fromStage; }
    public RecruitmentStage getToStage() { return toStage; }
    public UUID getOperatorAccountId() { return operatorAccountId; }
    public String getOperatorUsername() { return operatorUsername; }
    public String getNote() { return note; }
    public Instant getChangedAt() { return changedAt; }

    public record AccountEntitySnapshot(UUID id, String username) {
    }
}
