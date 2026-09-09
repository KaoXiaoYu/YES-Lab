package cn.yeslab.platform.discussion.model;

import cn.yeslab.platform.identity.model.AccountEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discussion_post_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "post_id"}))
public class DiscussionPostLikeEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "post_id", nullable = false)
    private DiscussionPostEntity post;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    protected DiscussionPostLikeEntity() { }
    public DiscussionPostLikeEntity(AccountEntity account, DiscussionPostEntity post) { this.account = account; this.post = post; }
}
