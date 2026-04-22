package com.plantogether.destination.repository;

import com.plantogether.destination.model.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, UUID> {

    List<Destination> findByTripIdOrderByCreatedAtDesc(UUID tripId);
}
