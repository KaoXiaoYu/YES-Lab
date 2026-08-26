package cn.yeslab.platform.achievement.model;

import cn.yeslab.platform.identity.model.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "achievement_news")
public class NewsEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 220) private String title;
    @Column(nullable = false, length = 120) private String sourceName;
    @Column(nullable = false, length = 800) private String sourceUrl;
    @Lob @Column(nullable = false) private String summary;
    @Column(nullable = false) private LocalDate publishedDate;
    @Column(nullable = false) private boolean visible;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "created_by_account_id", nullable = false, updatable = false)
    private AccountEntity createdBy;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    protected NewsEntity() {}
    public NewsEntity(String title, String sourceName, String sourceUrl, String summary, LocalDate publishedDate, boolean visible, AccountEntity createdBy) {
        update(title, sourceName, sourceUrl, summary, publishedDate, visible); this.createdBy = createdBy;
    }
    public void update(String title, String sourceName, String sourceUrl, String summary, LocalDate publishedDate, boolean visible) {
        this.title = title; this.sourceName = sourceName; this.sourceUrl = sourceUrl; this.summary = summary;
        this.publishedDate = publishedDate; this.visible = visible; this.updatedAt = Instant.now();
    }
    public UUID getId() { return id; } public String getTitle() { return title; } public String getSourceName() { return sourceName; }
    public String getSourceUrl() { return sourceUrl; } public String getSummary() { return summary; }
    public LocalDate getPublishedDate() { return publishedDate; } public boolean isVisible() { return visible; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
