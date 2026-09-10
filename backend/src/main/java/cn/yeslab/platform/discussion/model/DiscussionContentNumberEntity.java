package cn.yeslab.platform.discussion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discussion_content_numbers",
        uniqueConstraints = @UniqueConstraint(columnNames = "content_id"))
public class DiscussionContentNumberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DiscussionContentType contentType;

    @Column(nullable = false)
    private UUID contentId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected DiscussionContentNumberEntity() { }

    public DiscussionContentNumberEntity(DiscussionContentType contentType, UUID contentId, Instant createdAt) {
        this.contentType = contentType;
        this.contentId = contentId;
        this.createdAt = createdAt;
    }

    public long getSequenceNumber() { return sequenceNumber; }
}
