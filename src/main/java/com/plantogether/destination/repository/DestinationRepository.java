package com.plantogether.destination.repository;

import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, UUID> {

  List<Destination> findByTripIdOrderByCreatedAtDesc(UUID tripId);

  long countByTripId(UUID tripId);

  @Lock(LockModeType.PESSIMISTIC_READ)
  Optional<Destination> findByTripIdAndStatus(UUID tripId, DestinationStatus status);
}
