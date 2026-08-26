package cn.yeslab.platform.achievement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "competition_images")
public class CompetitionImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "competition_id", nullable = false)
    private CompetitionEntity competition;

    @Column(nullable = false, unique = true, length = 100)
    private String storedName;

    @Column(nullable = false, length = 255)
    private String originalName;

    @Column(nullable = false, length = 80)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false)
    private int displayOrder;

    protected CompetitionImageEntity() {
    }

    public CompetitionImageEntity(String storedName, String originalName, String contentType, long sizeBytes, String description, int displayOrder) {
        this.storedName = storedName;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.description = description;
        this.displayOrder = displayOrder;
    }

    void attachTo(CompetitionEntity competition) { this.competition = competition; }
    public UUID getId() { return id; }
    public String getStoredName() { return storedName; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getDescription() { return description; }
    public int getDisplayOrder() { return displayOrder; }
}
