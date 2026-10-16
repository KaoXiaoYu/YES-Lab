package cn.yeslab.platform.recruitment.form.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recruitment_form_submissions", uniqueConstraints = @UniqueConstraint(name = "uk_form_submission_request", columnNames = {"account_id", "request_key"}))
public class RecruitmentFormSubmissionEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID accountId;
    @Column(nullable = false) private UUID requestKey;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @Column(nullable = false) private UUID applicationId;
    @Column(nullable = false) private UUID formVersionId;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String resultJson;
    @Column(nullable = false) private Instant createdAt;

    protected RecruitmentFormSubmissionEntity() { }
    public RecruitmentFormSubmissionEntity(UUID account, UUID key, String fingerprint, UUID application, UUID version, String result) {
        accountId = account; requestKey = key; requestFingerprint = fingerprint;
        applicationId = application; formVersionId = version; resultJson = result; createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public UUID getRequestKey() { return requestKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getFormVersionId() { return formVersionId; }
    public String getResultJson() { return resultJson; }
    public Instant getCreatedAt() { return createdAt; }
}
