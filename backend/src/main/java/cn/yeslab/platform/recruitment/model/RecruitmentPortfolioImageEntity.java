package cn.yeslab.platform.recruitment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "recruitment_portfolio_images")
public class RecruitmentPortfolioImageEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "application_id", nullable = false)
    private UUID applicationId;
    @Column(name = "stored_name", nullable = false, unique = true, length = 100)
    private String storedName;
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
    @Column(name = "content_type", nullable = false, length = 80)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected RecruitmentPortfolioImageEntity() { }
    public RecruitmentPortfolioImageEntity(UUID applicationId, String storedName, String originalName,
                                           String contentType, long sizeBytes, int displayOrder) {
        this.applicationId = applicationId; this.storedName = storedName; this.originalName = originalName;
        this.contentType = contentType; this.sizeBytes = sizeBytes; this.displayOrder = displayOrder;
    }
    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public String getStoredName() { return storedName; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getDisplayOrder() { return displayOrder; }
}
