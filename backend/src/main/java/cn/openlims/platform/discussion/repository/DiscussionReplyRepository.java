package cn.openlims.platform.discussion.repository;

import cn.openlims.platform.discussion.model.DiscussionReplyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiscussionReplyRepository extends JpaRepository<DiscussionReplyEntity, UUID> {
    List<DiscussionReplyEntity> findByPostIdOrderByCreatedAtAsc(UUID postId);
    List<DiscussionReplyEntity> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);
    void deleteByPostId(UUID postId);
}
