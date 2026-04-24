package com.plantogether.destination.repository;

import com.plantogether.destination.model.DestinationVoteConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DestinationVoteConfigRepository extends JpaRepository<DestinationVoteConfig, UUID> {
}
