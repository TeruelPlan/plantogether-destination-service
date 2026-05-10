package com.plantogether.destination.service;

import com.plantogether.common.grpc.TripClient;
import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.dto.ProposeDestinationRequest;
import com.plantogether.destination.exception.DestinationAlreadyChosenException;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationStatus;
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
  private final TripClient tripClient;

  @Transactional
  public DestinationResponse proposeDestination(
      UUID tripId, String deviceId, ProposeDestinationRequest req) {
    var membership = tripClient.requireMembership(tripId.toString(), deviceId);
    UUID memberUuid =
        membership.tripMemberId() != null ? UUID.fromString(membership.tripMemberId()) : null;

    requireNotChosen(tripId);

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
            .proposedByTripMemberId(memberUuid)
            .build();

    Destination saved = repository.save(entity);
    return DestinationResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<DestinationResponse> listDestinations(UUID tripId, String deviceId) {
    tripClient.requireMembership(tripId.toString(), deviceId);
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

  private void requireNotChosen(UUID tripId) {
    if (repository.findByTripIdAndStatus(tripId, DestinationStatus.CHOSEN).isPresent()) {
      throw new DestinationAlreadyChosenException();
    }
  }
}
