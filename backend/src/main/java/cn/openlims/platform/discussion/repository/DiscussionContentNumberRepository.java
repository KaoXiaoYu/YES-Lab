package cn.openlims.platform.discussion.repository;

import cn.openlims.platform.discussion.model.DiscussionContentNumberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionContentNumberRepository extends JpaRepository<DiscussionContentNumberEntity, Long> {
    Optional<DiscussionContentNumberEntity> findByContentId(UUID contentId);
}
