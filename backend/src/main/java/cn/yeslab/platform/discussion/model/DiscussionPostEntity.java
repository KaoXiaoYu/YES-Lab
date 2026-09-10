package cn.yeslab.platform.discussion.model;

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
@Table(name = "discussion_posts")
public class DiscussionPostEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "author_account_id", nullable = false)
    private AccountEntity author;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(nullable = false)
    private boolean announcement;

    @Column(nullable = false)
    private boolean pinned;

    private Instant pinnedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected DiscussionPostEntity() { }

    public DiscussionPostEntity(AccountEntity author, String title, String content, boolean announcement) {
        this.author = author;
        this.announcement = announcement;
        update(title, content);
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AccountEntity getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isAnnouncement() { return announcement; }
    public boolean isPinned() { return pinned; }
    public Instant getPinnedAt() { return pinnedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public void togglePinned() {
        this.pinned = !this.pinned;
        this.pinnedAt = this.pinned ? Instant.now() : null;
    }
}
