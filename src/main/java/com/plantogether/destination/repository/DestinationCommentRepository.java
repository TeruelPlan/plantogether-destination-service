package com.plantogether.destination.repository;

import com.plantogether.destination.model.DestinationComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DestinationCommentRepository extends JpaRepository<DestinationComment, UUID> {

  List<DestinationComment> findByDestinationIdOrderByCreatedAtAscIdAsc(UUID destinationId);
}
