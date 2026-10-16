package cn.yeslab.platform.recruitment.form.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// Storage and authenticated downloads are implemented in the next batch.
@Entity
@Table(name = "recruitment_form_attachments", indexes = @Index(name = "idx_form_attachment_application", columnList = "application_id,module_id"))
public class RecruitmentFormAttachmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID applicationId;
    @Column(nullable = false) private UUID ownerAccountId;
    @Column(nullable = false) private UUID moduleId;
    @Column(nullable = false, length = 255, unique = true) private String storedName;
    @Column(nullable = false, length = 255) private String originalName;
    @Column(nullable = false, length = 80) private String contentType;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false) private int displayOrder;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private Instant createdAt;

    protected RecruitmentFormAttachmentEntity() { }
    public RecruitmentFormAttachmentEntity(UUID application, UUID owner, UUID module, String stored,
            String original, String type, long bytes, int order) {
        applicationId = application; ownerAccountId = owner; moduleId = module;
        storedName = stored; originalName = original; contentType = type;
        sizeBytes = bytes; displayOrder = order; createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getOwnerAccountId() { return ownerAccountId; }
    public UUID getModuleId() { return moduleId; }
    public String getStoredName() { return storedName; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public void detach() { active = false; }
}
