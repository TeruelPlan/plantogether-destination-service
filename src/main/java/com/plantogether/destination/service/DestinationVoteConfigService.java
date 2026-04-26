package com.plantogether.destination.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.grpc.Role;
import com.plantogether.common.grpc.TripClient;
import com.plantogether.common.grpc.TripMembership;
import com.plantogether.destination.dto.VoteConfigResponse;
import com.plantogether.destination.exception.DestinationAlreadyChosenException;
import com.plantogether.destination.model.DestinationStatus;
import com.plantogether.destination.model.DestinationVoteConfig;
import com.plantogether.destination.model.VoteMode;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.destination.repository.DestinationVoteConfigRepository;
import com.plantogether.destination.repository.DestinationVoteRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DestinationVoteConfigService {

  private final DestinationVoteConfigRepository configRepository;
  private final DestinationVoteRepository voteRepository;
  private final DestinationRepository destinationRepository;
  private final TripClient tripClient;
  private final TripLockService tripLockService;

  @Transactional(readOnly = true)
  public VoteConfigResponse getConfig(UUID tripId, String deviceId) {
    tripClient.requireMembership(tripId.toString(), deviceId);
    return configRepository
        .findById(tripId)
        .map(
            c ->
                VoteConfigResponse.builder()
                    .tripId(c.getTripId())
                    .mode(c.getMode())
                    .updatedAt(c.getUpdatedAt())
                    .build())
        .orElse(
            VoteConfigResponse.builder()
                .tripId(tripId)
                .mode(VoteMode.SIMPLE)
                .updatedAt(Instant.EPOCH)
                .build());
  }

  @Transactional
  public VoteConfigResponse upsertConfig(UUID tripId, String deviceId, VoteMode newMode) {
    TripMembership membership = tripClient.requireMembership(tripId.toString(), deviceId);
    if (membership.role() != Role.ORGANIZER) {
      throw new AccessDeniedException("Only the trip organizer can configure the vote mode");
    }

    requireNotChosen(tripId);

    // Serialize against concurrent castVote on the same trip so two flips can't both
    // pass the no-op check and write conflicting modes.
    tripLockService.lock(tripId);

    DestinationVoteConfig config =
        configRepository
            .findById(tripId)
            .orElse(DestinationVoteConfig.builder().tripId(tripId).build());

    if (config.getMode() == newMode) {
      return VoteConfigResponse.builder()
          .tripId(config.getTripId())
          .mode(config.getMode())
          .updatedAt(config.getUpdatedAt())
          .build();
    }

    config.setMode(newMode);
    config.setUpdatedAt(Instant.now());
    DestinationVoteConfig saved = configRepository.save(config);

    if (newMode == VoteMode.SIMPLE || newMode == VoteMode.APPROVAL) {
      voteRepository.nullRanksForTrip(tripId);
    }

    return VoteConfigResponse.builder()
        .tripId(saved.getTripId())
        .mode(saved.getMode())
        .updatedAt(saved.getUpdatedAt())
        .build();
  }

  private void requireNotChosen(UUID tripId) {
    if (destinationRepository.findByTripIdAndStatus(tripId, DestinationStatus.CHOSEN).isPresent()) {
      throw new DestinationAlreadyChosenException();
    }
  }
}
