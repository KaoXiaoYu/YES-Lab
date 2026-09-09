package cn.yeslab.platform.discussion.repository;

import cn.yeslab.platform.discussion.model.DiscussionPostLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionPostLikeRepository extends JpaRepository<DiscussionPostLikeEntity, UUID> {
    long countByPostId(UUID postId);
    boolean existsByPostIdAndAccountId(UUID postId, UUID accountId);
    Optional<DiscussionPostLikeEntity> findByPostIdAndAccountId(UUID postId, UUID accountId);
    void deleteByPostId(UUID postId);
}
