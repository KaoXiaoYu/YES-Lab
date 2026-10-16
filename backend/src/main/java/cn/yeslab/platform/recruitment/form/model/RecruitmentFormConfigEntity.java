package cn.yeslab.platform.recruitment.form.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recruitment_form_config")
public class RecruitmentFormConfigEntity {
    @Id private Long id;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String draftSchemaJson;
    @Column(nullable = false) private long draftRevision;
    @Column(nullable = false) private long publishedDraftRevision;
    @Column(nullable = false) private UUID publishedVersionId;
    @Column(nullable = false, length = 190) private String updatedBy;
    @Column(nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long lockVersion;

    protected RecruitmentFormConfigEntity() { }
    public RecruitmentFormConfigEntity(String schema, UUID initialVersionId) {
        id = 1L;
        draftSchemaJson = schema;
        publishedVersionId = initialVersionId;
        updatedBy = "SYSTEM";
        updatedAt = Instant.now();
    }
    public void saveDraft(String schema, String actor) {
        draftSchemaJson = schema;
        draftRevision++;
        updatedBy = actor;
        updatedAt = Instant.now();
    }
    public void publish(UUID versionId) {
        publishedVersionId = versionId;
        publishedDraftRevision = draftRevision;
    }
    public Long getId() { return id; }
    public String getDraftSchemaJson() { return draftSchemaJson; }
    public long getDraftRevision() { return draftRevision; }
    public long getPublishedDraftRevision() { return publishedDraftRevision; }
    public UUID getPublishedVersionId() { return publishedVersionId; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
