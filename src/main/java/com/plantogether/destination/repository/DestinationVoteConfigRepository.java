package com.plantogether.destination.repository;

import com.plantogether.destination.model.DestinationVoteConfig;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationVoteConfigRepository
    extends JpaRepository<DestinationVoteConfig, UUID> {}
