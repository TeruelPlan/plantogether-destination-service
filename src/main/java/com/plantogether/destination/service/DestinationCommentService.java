package com.plantogether.destination.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.destination.dto.AddCommentRequest;
import com.plantogether.destination.dto.CommentResponse;
import com.plantogether.destination.event.DestinationCommentAddedInternalEvent;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationComment;
import com.plantogether.destination.repository.DestinationCommentRepository;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.trip.grpc.TripMemberProto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationCommentService {

  private static final String UNKNOWN_MEMBER = "Unknown member";

  private final DestinationRepository destinationRepository;
  private final DestinationCommentRepository commentRepository;
  private final TripGrpcClient tripGrpcClient;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public CommentResponse addComment(UUID destinationId, String deviceIdStr, AddCommentRequest req) {
    Destination destination = loadDestination(destinationId);
    Map<UUID, String> displayNames = authorizeAndResolveMembers(destination, deviceIdStr);

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

    return CommentResponse.from(saved, displayNames.getOrDefault(deviceUuid, UNKNOWN_MEMBER));
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

  /**
   * Single gRPC roundtrip: fetches members and uses the same list for the membership check and
   * display-name resolution. Replaces the previous {@code isMember + getTripMembers} pair.
   */
  private Map<UUID, String> authorizeAndResolveMembers(
      Destination destination, String deviceIdStr) {
    List<TripMemberProto> members =
        tripGrpcClient.getTripMembers(destination.getTripId().toString());
    boolean isMember = members.stream().anyMatch(m -> m.getDeviceId().equals(deviceIdStr));
    if (!isMember) {
      throw new AccessDeniedException("Device is not a member of this trip");
    }
    Map<UUID, String> displayNames = new HashMap<>();
    for (TripMemberProto m : members) {
      UUID memberId;
      try {
        memberId = UUID.fromString(m.getDeviceId());
      } catch (IllegalArgumentException ex) {
        log.warn("Skipping malformed member device id from trip-service: {}", m.getDeviceId());
        continue;
      }
      String name = m.getDisplayName();
      displayNames.putIfAbsent(memberId, (name == null || name.isBlank()) ? UNKNOWN_MEMBER : name);
    }
    return displayNames;
  }
}
