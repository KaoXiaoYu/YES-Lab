package cn.yeslab.platform.discussion.repository;

import cn.yeslab.platform.discussion.model.DiscussionReplyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiscussionReplyRepository extends JpaRepository<DiscussionReplyEntity, UUID> {
    List<DiscussionReplyEntity> findByPostIdOrderByCreatedAtAsc(UUID postId);
    void deleteByPostId(UUID postId);
}
