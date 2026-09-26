package cn.yeslab.platform.task.repository;

import cn.yeslab.platform.task.model.BountyPrizeFulfillmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BountyPrizeFulfillmentRepository extends JpaRepository<BountyPrizeFulfillmentEntity, UUID> {

    Optional<BountyPrizeFulfillmentEntity> findByAssignment_Id(UUID assignmentId);

    List<BountyPrizeFulfillmentEntity> findByAssignment_IdIn(Collection<UUID> assignmentIds);
}
