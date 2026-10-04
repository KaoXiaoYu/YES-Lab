package cn.yeslab.platform.fund.model;

import jakarta.persistence.*;
import cn.yeslab.platform.identity.model.AccountEntity;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "fund_entries", uniqueConstraints = {
    @UniqueConstraint(name = "uk_fund_request", columnNames = "request_key"),
    @UniqueConstraint(name = "uk_fund_reversal", columnNames = "original_entry_id")
})
public class FundEntryEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16)") private FundEntryType type;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private LocalDate occurredOn;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 1000) private String description;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "operator_account_id", nullable = false) private AccountEntity operator;
    @Column(nullable = false, length = 80) private String operatorName;
    @Column(nullable = false) private Instant recordedAt;
    @Column(name = "request_key", nullable = false, length = 36) private String requestKey;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "original_entry_id") private FundEntryEntity original;
    @Column(length = 1000) private String reversalReason;
    protected FundEntryEntity() {}
    public FundEntryEntity(FundEntryType type, BigDecimal amount, LocalDate date, String title, String description,
            AccountEntity operator, String operatorName, String requestKey, String fingerprint, FundEntryEntity original, String reason) {
        this.type = type; this.amount = amount; occurredOn = date; this.title = title; this.description = description;
        this.operator = operator; this.operatorName = operatorName; recordedAt = Instant.now();
        this.requestKey = requestKey; requestFingerprint = fingerprint; this.original = original; reversalReason = reason;
    }
    public UUID getId() { return id; }
    public FundEntryType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public AccountEntity getOperator() { return operator; }
    public String getOperatorName() { return operatorName; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public FundEntryEntity getOriginal() { return original; }
    public String getReversalReason() { return reversalReason; }
}
