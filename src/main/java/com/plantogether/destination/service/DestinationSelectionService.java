package com.plantogether.destination.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.common.grpc.Role;
import com.plantogether.common.grpc.TripClient;
import com.plantogether.common.grpc.TripMembership;
import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.event.DestinationChosenInternalEvent;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationStatus;
import com.plantogether.destination.repository.DestinationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DestinationSelectionService {

  private final DestinationRepository repository;
  private final TripClient tripClient;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public DestinationResponse selectDestination(UUID destinationId, String deviceId) {
    Destination destination =
        repository
            .findById(destinationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Destination not found: " + destinationId));

    UUID tripId = destination.getTripId();

    TripMembership membership = tripClient.requireMembership(tripId.toString(), deviceId);
    if (membership.role() != Role.ORGANIZER) {
      throw new AccessDeniedException("Only the trip organizer can select a destination");
    }

    if (destination.getStatus() == DestinationStatus.CHOSEN) {
      return DestinationResponse.from(destination);
    }

    Optional<Destination> previous =
        repository.findByTripIdAndStatus(tripId, DestinationStatus.CHOSEN);

    UUID previousChosenId = null;
    if (previous.isPresent() && !previous.get().getId().equals(destinationId)) {
      Destination prev = previous.get();
      prev.setStatus(DestinationStatus.PROPOSED);
      prev.setChosenAt(null);
      prev.setChosenBy(null);
      repository.save(prev);
      repository.flush();
      previousChosenId = prev.getId();
    }

    Instant chosenAt = Instant.now();
    UUID deviceUuid = UUID.fromString(deviceId);
    destination.setStatus(DestinationStatus.CHOSEN);
    destination.setChosenAt(chosenAt);
    destination.setChosenBy(deviceUuid);
    Destination saved = repository.save(destination);

    eventPublisher.publishEvent(
        new DestinationChosenInternalEvent(
            tripId, saved.getId(), saved.getName(), deviceUuid, chosenAt, previousChosenId));

    return DestinationResponse.from(saved);
  }
}
