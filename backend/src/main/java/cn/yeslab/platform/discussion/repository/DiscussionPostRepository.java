package cn.yeslab.platform.discussion.repository;

import cn.yeslab.platform.discussion.model.DiscussionPostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiscussionPostRepository extends JpaRepository<DiscussionPostEntity, UUID> {
    List<DiscussionPostEntity> findTop50ByOrderByCreatedAtDesc();
}
