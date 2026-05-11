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
    var membership = tripClient.requireMembership(destination.getTripId().toString(), deviceIdStr);
    UUID memberUuid = UUID.fromString(membership.tripMemberId());

    String authorName = resolveDisplayName(destination.getTripId(), memberUuid);

    DestinationComment saved =
        commentRepository.save(
            DestinationComment.builder()
                .destinationId(destinationId)
                .tripId(destination.getTripId())
                .tripMemberId(memberUuid)
                .content(req.getContent())
                .build());

    eventPublisher.publishEvent(
        new DestinationCommentAddedInternalEvent(
            saved.getTripId(),
            saved.getDestinationId(),
            saved.getId(),
            saved.getTripMemberId(),
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
                CommentResponse.from(
                    c, displayNames.getOrDefault(c.getTripMemberId(), UNKNOWN_MEMBER)))
        .toList();
  }

  private Destination loadDestination(UUID destinationId) {
    return destinationRepository
        .findById(destinationId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Destination not found with id: " + destinationId));
  }

  private String resolveDisplayName(UUID tripId, UUID memberUuid) {
    return tripClient.getTripMembers(tripId.toString()).stream()
        .filter(m -> memberUuid.toString().equals(m.tripMemberId()))
        .findFirst()
        .map(
            m ->
                (m.displayName() == null || m.displayName().isBlank())
                    ? UNKNOWN_MEMBER
                    : m.displayName())
        .orElse(UNKNOWN_MEMBER);
  }

  private Map<UUID, String> authorizeAndResolveMembers(
      Destination destination, String deviceIdStr) {
    tripClient.requireMembership(destination.getTripId().toString(), deviceIdStr);
    List<TripMember> members = tripClient.getTripMembers(destination.getTripId().toString());
    if (members.isEmpty()) {
      throw new AccessDeniedException("Trip has no members");
    }
    Map<UUID, String> displayNames = new HashMap<>();
    for (TripMember m : members) {
      String name = m.displayName();
      displayNames.putIfAbsent(
          UUID.fromString(m.tripMemberId()),
          (name == null || name.isBlank()) ? UNKNOWN_MEMBER : name);
    }
    return displayNames;
  }
}
