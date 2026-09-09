package cn.yeslab.platform.notification.model;

import cn.yeslab.platform.identity.model.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_account_id", nullable = false)
    private AccountEntity recipient;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(length = 300)
    private String targetPath;

    @Column(length = 190)
    private String groupKey;

    @Column(nullable = false)
    private int aggregationCount = 1;

    private Instant readAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected NotificationEntity() { }

    public NotificationEntity(AccountEntity recipient, String type, String title, String summary,
                              String targetPath, String groupKey) {
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.summary = summary;
        this.targetPath = targetPath;
        this.groupKey = groupKey;
    }

    public UUID getId() { return id; }
    public AccountEntity getRecipient() { return recipient; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getTargetPath() { return targetPath; }
    public int getAggregationCount() { return aggregationCount; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void aggregate(String title, String summary) {
        this.aggregationCount++;
        this.title = title;
        this.summary = summary;
        this.updatedAt = Instant.now();
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
            updatedAt = readAt;
        }
    }
}
