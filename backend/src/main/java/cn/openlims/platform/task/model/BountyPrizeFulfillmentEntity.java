package cn.openlims.platform.task.model;

import cn.openlims.platform.identity.model.AccountEntity;
import cn.openlims.platform.identity.model.MemberProfileEntity;
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

import java.time.Instant;
import java.util.UUID;

/** 单个悬赏接取对象的线下奖金履约记录。记录保留，不覆盖已撤销的获奖历史。 */
@Entity
@Table(name = "bounty_prize_fulfillments")
public class BountyPrizeFulfillmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_assignment_id", nullable = false, unique = true)
    private TaskAssignmentEntity assignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BountyPrizeFulfillmentStatus status = BountyPrizeFulfillmentStatus.PENDING;

    private Instant issuedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_account_id")
    private AccountEntity issuedBy;

    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_profile_id")
    private MemberProfileEntity receivedBy;

    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_account_id")
    private AccountEntity revokedBy;

    @Column(length = 1000)
    private String revokedReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected BountyPrizeFulfillmentEntity() {
    }

    public BountyPrizeFulfillmentEntity(TaskAssignmentEntity assignment) {
        this.assignment = assignment;
    }

    public UUID getId() { return id; }
    public TaskAssignmentEntity getAssignment() { return assignment; }
    public BountyPrizeFulfillmentStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public AccountEntity getIssuedBy() { return issuedBy; }
    public Instant getReceivedAt() { return receivedAt; }
    public MemberProfileEntity getReceivedBy() { return receivedBy; }
    public Instant getRevokedAt() { return revokedAt; }
    public AccountEntity getRevokedBy() { return revokedBy; }
    public String getRevokedReason() { return revokedReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markIssued(AccountEntity operator) {
        requireStatus(BountyPrizeFulfillmentStatus.PENDING);
        status = BountyPrizeFulfillmentStatus.ISSUED;
        issuedAt = Instant.now();
        issuedBy = operator;
        updatedAt = Instant.now();
    }

    public void markReceived(MemberProfileEntity recipient) {
        requireStatus(BountyPrizeFulfillmentStatus.ISSUED);
        status = BountyPrizeFulfillmentStatus.RECEIVED;
        receivedAt = Instant.now();
        receivedBy = recipient;
        updatedAt = Instant.now();
    }

    public void revokePending(AccountEntity operator, String reason) {
        requireStatus(BountyPrizeFulfillmentStatus.PENDING);
        status = BountyPrizeFulfillmentStatus.REVOKED;
        revokedAt = Instant.now();
        revokedBy = operator;
        revokedReason = reason;
        updatedAt = Instant.now();
    }

    private void requireStatus(BountyPrizeFulfillmentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("奖金履约状态必须为 " + expected + "，当前为 " + status);
        }
    }
}
