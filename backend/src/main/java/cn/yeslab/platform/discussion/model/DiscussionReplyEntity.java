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
@Table(name = "discussion_replies")
public class DiscussionReplyEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private DiscussionPostEntity post;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "author_account_id", nullable = false)
    private AccountEntity author;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected DiscussionReplyEntity() { }

    public DiscussionReplyEntity(DiscussionPostEntity post, AccountEntity author, String content) {
        this.post = post;
        this.author = author;
        update(content);
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public DiscussionPostEntity getPost() { return post; }
    public AccountEntity getAuthor() { return author; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void update(String content) { this.content = content; this.updatedAt = Instant.now(); }
}
