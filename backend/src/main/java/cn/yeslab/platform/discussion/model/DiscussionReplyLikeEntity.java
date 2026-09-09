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
@Table(name = "discussion_reply_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "reply_id"}))
public class DiscussionReplyLikeEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "reply_id", nullable = false)
    private DiscussionReplyEntity reply;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    protected DiscussionReplyLikeEntity() { }
    public DiscussionReplyLikeEntity(AccountEntity account, DiscussionReplyEntity reply) { this.account = account; this.reply = reply; }
}
