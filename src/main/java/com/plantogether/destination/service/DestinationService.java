package com.plantogether.destination.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.dto.ProposeDestinationRequest;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationVote;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.destination.repository.DestinationVoteRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DestinationService {

  private final DestinationRepository repository;
  private final DestinationVoteRepository voteRepository;
  private final TripGrpcClient tripGrpcClient;

  @Transactional
  public DestinationResponse proposeDestination(
      UUID tripId, String deviceId, ProposeDestinationRequest req) {
    requireMember(tripId, deviceId);

    Destination entity =
        Destination.builder()
            .tripId(tripId)
            .name(req.getName())
            .description(req.getDescription())
            .imageKey(req.getImageKey())
            .estimatedBudget(req.getEstimatedBudget())
            .currency(req.getCurrency())
            .externalUrl(req.getExternalUrl())
            .proposedBy(UUID.fromString(deviceId))
            .build();

    Destination saved = repository.save(entity);
    return DestinationResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<DestinationResponse> listDestinations(UUID tripId, String deviceId) {
    requireMember(tripId, deviceId);
    List<Destination> destinations = repository.findByTripIdOrderByCreatedAtDesc(tripId);
    if (destinations.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = destinations.stream().map(Destination::getId).toList();
    Map<UUID, List<DestinationVote>> votesByDestination =
        voteRepository.findByDestinationIdIn(ids).stream()
            .collect(Collectors.groupingBy(DestinationVote::getDestinationId));
    UUID deviceUuid = UUID.fromString(deviceId);
    return destinations.stream()
        .map(
            d ->
                DestinationResponse.from(
                    d, votesByDestination.getOrDefault(d.getId(), List.of()), deviceUuid))
        .toList();
  }

  private void requireMember(UUID tripId, String deviceId) {
    if (!tripGrpcClient.isMember(tripId.toString(), deviceId)) {
      throw new AccessDeniedException("Device is not a member of this trip");
    }
  }
}
