package com.plantogether.destination.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.common.grpc.TripClient;
import com.plantogether.common.grpc.TripMember;
import com.plantogether.destination.dto.AddCommentRequest;
import com.plantogether.destination.dto.CommentResponse;
import com.plantogether.destination.event.DestinationCommentAddedInternalEvent;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationComment;
import com.plantogether.destination.repository.DestinationCommentRepository;
import com.plantogether.destination.repository.DestinationRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DestinationCommentService {

  private static final String UNKNOWN_MEMBER = "Unknown member";

  private final DestinationRepository destinationRepository;
  private final DestinationCommentRepository commentRepository;
  private final TripClient tripClient;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public CommentResponse addComment(UUID destinationId, String deviceIdStr, AddCommentRequest req) {
    Destination destination = loadDestination(destinationId);
    String authorName = authorizeAndGetDisplayName(destination, deviceIdStr);

    UUID deviceUuid = UUID.fromString(deviceIdStr);
    DestinationComment saved =
        commentRepository.save(
            DestinationComment.builder()
                .destinationId(destinationId)
                .tripId(destination.getTripId())
                .deviceId(deviceUuid)
                .content(req.getContent())
                .build());

    eventPublisher.publishEvent(
        new DestinationCommentAddedInternalEvent(
            saved.getTripId(),
            saved.getDestinationId(),
            saved.getId(),
            saved.getDeviceId(),
            saved.getCreatedAt()));

    return CommentResponse.from(saved, authorName);
  }

  @Transactional(readOnly = true)
  public List<CommentResponse> listComments(UUID destinationId, String deviceIdStr) {
    Destination destination = loadDestination(destinationId);
    Map<UUID, String> displayNames = authorizeAndResolveMembers(destination, deviceIdStr);

    List<DestinationComment> comments =
        commentRepository.findByDestinationIdOrderByCreatedAtAscIdAsc(destinationId);
    if (comments.isEmpty()) {
      return List.of();
    }

    return comments.stream()
        .map(
            c ->
                CommentResponse.from(c, displayNames.getOrDefault(c.getDeviceId(), UNKNOWN_MEMBER)))
        .toList();
  }

  private Destination loadDestination(UUID destinationId) {
    return destinationRepository
        .findById(destinationId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Destination not found with id: " + destinationId));
  }

  private String authorizeAndGetDisplayName(Destination destination, String deviceIdStr) {
    List<TripMember> members = tripClient.getTripMembers(destination.getTripId().toString());
    return members.stream()
        .filter(m -> m.deviceId().toString().equals(deviceIdStr))
        .findFirst()
        .map(
            m ->
                (m.displayName() == null || m.displayName().isBlank())
                    ? UNKNOWN_MEMBER
                    : m.displayName())
        .orElseThrow(() -> new AccessDeniedException("Device is not a member of this trip"));
  }

  private Map<UUID, String> authorizeAndResolveMembers(
      Destination destination, String deviceIdStr) {
    List<TripMember> members = tripClient.getTripMembers(destination.getTripId().toString());
    boolean isMember = members.stream().anyMatch(m -> m.deviceId().toString().equals(deviceIdStr));
    if (!isMember) {
      throw new AccessDeniedException("Device is not a member of this trip");
    }
    Map<UUID, String> displayNames = new HashMap<>();
    for (TripMember m : members) {
      String name = m.displayName();
      displayNames.putIfAbsent(
          m.deviceId(), (name == null || name.isBlank()) ? UNKNOWN_MEMBER : name);
    }
    return displayNames;
  }
}
