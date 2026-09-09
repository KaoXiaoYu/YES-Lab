package cn.yeslab.platform.discussion.repository;

import cn.yeslab.platform.discussion.model.DiscussionReplyLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionReplyLikeRepository extends JpaRepository<DiscussionReplyLikeEntity, UUID> {
    long countByReplyId(UUID replyId);
    boolean existsByReplyIdAndAccountId(UUID replyId, UUID accountId);
    Optional<DiscussionReplyLikeEntity> findByReplyIdAndAccountId(UUID replyId, UUID accountId);
    void deleteByReplyId(UUID replyId);
    void deleteByReplyPostId(UUID postId);
}
