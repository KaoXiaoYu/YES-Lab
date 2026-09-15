package cn.yeslab.platform.points.repository;

import cn.yeslab.platform.points.model.PointGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PointGrantRepository extends JpaRepository<PointGrantEntity, UUID> {
    boolean existsBySourceReferenceIgnoreCase(String sourceReference);
    boolean existsByReversalOf_Id(UUID grantId);
    List<PointGrantEntity> findTop200ByOrderByCreatedAtDesc();
    Optional<PointGrantEntity> findById(UUID id);
}
