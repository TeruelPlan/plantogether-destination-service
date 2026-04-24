package com.plantogether.destination.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.destination.dto.VoteConfigResponse;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.DestinationVoteConfig;
import com.plantogether.destination.model.VoteMode;
import com.plantogether.destination.repository.DestinationVoteConfigRepository;
import com.plantogether.destination.repository.DestinationVoteRepository;
import com.plantogether.trip.grpc.IsMemberResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DestinationVoteConfigService {

  private static final String ROLE_ORGANIZER = "ORGANIZER";

  private final DestinationVoteConfigRepository configRepository;
  private final DestinationVoteRepository voteRepository;
  private final TripGrpcClient tripGrpcClient;
  private final TripLockService tripLockService;

  @Transactional(readOnly = true)
  public VoteConfigResponse getConfig(UUID tripId, String deviceId) {
    if (!tripGrpcClient.isMember(tripId.toString(), deviceId)) {
      throw new AccessDeniedException("Device is not a member of this trip");
    }
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
    IsMemberResponse membership = tripGrpcClient.isMemberWithRole(tripId.toString(), deviceId);
    if (!membership.getIsMember()) {
      throw new AccessDeniedException("Device is not a member of this trip");
    }
    if (!ROLE_ORGANIZER.equals(membership.getRole())) {
      throw new AccessDeniedException("Only the trip organizer can configure the vote mode");
    }

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
}
