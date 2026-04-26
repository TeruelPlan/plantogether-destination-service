package com.plantogether.destination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.common.grpc.Role;
import com.plantogether.common.grpc.TripClient;
import com.plantogether.common.grpc.TripMember;
import com.plantogether.destination.dto.AddCommentRequest;
import com.plantogether.destination.dto.CommentResponse;
import com.plantogether.destination.event.DestinationCommentAddedInternalEvent;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationComment;
import com.plantogether.destination.repository.DestinationCommentRepository;
import com.plantogether.destination.repository.DestinationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DestinationCommentServiceTest {

  @Mock private DestinationRepository destinationRepository;
  @Mock private DestinationCommentRepository commentRepository;
  @Mock private TripClient tripClient;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private DestinationCommentService service;

  private UUID tripId;
  private UUID destinationId;
  private UUID deviceUuid;
  private String deviceId;
  private Destination destination;

  @BeforeEach
  void setUp() {
    tripId = UUID.randomUUID();
    destinationId = UUID.randomUUID();
    deviceUuid = UUID.randomUUID();
    deviceId = deviceUuid.toString();
    destination =
        Destination.builder()
            .id(destinationId)
            .tripId(tripId)
            .name("Paris")
            .proposedBy(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  private TripMember member(UUID id, String name) {
    return new TripMember(id, name, Role.PARTICIPANT);
  }

  private DestinationComment persistedComment(UUID id, String content, Instant createdAt) {
    return DestinationComment.builder()
        .id(id)
        .destinationId(destinationId)
        .tripId(tripId)
        .deviceId(deviceUuid)
        .content(content)
        .createdAt(createdAt)
        .build();
  }

  @Test
  void addComment_validMember_persistsAndReturnsResponseWithDisplayName() {
    when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
    when(tripClient.getTripMembers(tripId.toString()))
        .thenReturn(List.of(member(deviceUuid, "Alice")));
    UUID commentId = UUID.randomUUID();
    Instant now = Instant.now();
    when(commentRepository.save(any(DestinationComment.class)))
        .thenAnswer(
            inv -> {
              DestinationComment c = inv.getArgument(0);
              c.setId(commentId);
              c.setCreatedAt(now);
              return c;
            });

    CommentResponse resp =
        service.addComment(
            destinationId, deviceId, AddCommentRequest.builder().content("Looks great!").build());

    assertThat(resp.getId()).isEqualTo(commentId);
    assertThat(resp.getAuthorDeviceId()).isEqualTo(deviceUuid);
    assertThat(resp.getAuthorDisplayName()).isEqualTo("Alice");
    assertThat(resp.getContent()).isEqualTo("Looks great!");
  }

  @Test
  void addComment_nonMember_throwsAccessDenied() {
    when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
    when(tripClient.getTripMembers(tripId.toString()))
        .thenReturn(List.of(member(UUID.randomUUID(), "Someone else")));

    assertThatThrownBy(
            () ->
                service.addComment(
                    destinationId, deviceId, AddCommentRequest.builder().content("hi").build()))
        .isInstanceOf(AccessDeniedException.class);

    verify(commentRepository, never()).save(any());
  }

  @Test
  void addComment_unknownDestination_throwsResourceNotFound() {
    when(destinationRepository.findById(destinationId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.addComment(
                    destinationId, deviceId, AddCommentRequest.builder().content("hi").build()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void addComment_publishesInternalEventOnce() {
    when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
    when(tripClient.getTripMembers(tripId.toString()))
        .thenReturn(List.of(member(deviceUuid, "Alice")));
    UUID commentId = UUID.randomUUID();
    Instant ts = Instant.parse("2026-04-25T10:00:00Z");
    when(commentRepository.save(any(DestinationComment.class)))
        .thenAnswer(
            inv -> {
              DestinationComment c = inv.getArgument(0);
              c.setId(commentId);
              c.setCreatedAt(ts);
              return c;
            });

    service.addComment(destinationId, deviceId, AddCommentRequest.builder().content("hey").build());

    ArgumentCaptor<DestinationCommentAddedInternalEvent> captor =
        ArgumentCaptor.forClass(DestinationCommentAddedInternalEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    DestinationCommentAddedInternalEvent e = captor.getValue();
    assertThat(e.tripId()).isEqualTo(tripId);
    assertThat(e.destinationId()).isEqualTo(destinationId);
    assertThat(e.commentId()).isEqualTo(commentId);
    assertThat(e.authorDeviceId()).isEqualTo(deviceUuid);
    assertThat(e.occurredAt()).isEqualTo(ts);
  }

  @Test
  void listComments_returnsComments_orderedAsRepositoryReturns() {
    when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
    Instant t = Instant.parse("2026-04-25T10:00:00Z");
    DestinationComment c1 = persistedComment(UUID.randomUUID(), "first", t);
    DestinationComment c2 = persistedComment(UUID.randomUUID(), "second", t);
    DestinationComment c3 = persistedComment(UUID.randomUUID(), "third", t);
    when(commentRepository.findByDestinationIdOrderByCreatedAtAscIdAsc(destinationId))
        .thenReturn(List.of(c1, c2, c3));
    when(tripClient.getTripMembers(tripId.toString()))
        .thenReturn(List.of(member(deviceUuid, "Alice")));

    List<CommentResponse> result = service.listComments(destinationId, deviceId);

    assertThat(result)
        .extracting(CommentResponse::getContent)
        .containsExactly("first", "second", "third");
    assertThat(result).allSatisfy(r -> assertThat(r.getAuthorDisplayName()).isEqualTo("Alice"));
  }

  @Test
  void listComments_nonMember_throwsAccessDenied() {
    when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
    when(tripClient.getTripMembers(tripId.toString()))
        .thenReturn(List.of(member(UUID.randomUUID(), "Someone else")));

    assertThatThrownBy(() -> service.listComments(destinationId, deviceId))
        .isInstanceOf(AccessDeniedException.class);

    verify(commentRepository, never()).findByDestinationIdOrderByCreatedAtAscIdAsc(any());
  }

  @Test
  void listComments_unknownDestination_throwsResourceNotFound() {
    when(destinationRepository.findById(destinationId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listComments(destinationId, deviceId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void listComments_emptyList_returnsEmpty() {
    when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
    when(commentRepository.findByDestinationIdOrderByCreatedAtAscIdAsc(destinationId))
        .thenReturn(List.of());
    when(tripClient.getTripMembers(tripId.toString()))
        .thenReturn(List.of(member(deviceUuid, "Alice")));

    List<CommentResponse> result = service.listComments(destinationId, deviceId);

    assertThat(result).isEmpty();
  }
}
