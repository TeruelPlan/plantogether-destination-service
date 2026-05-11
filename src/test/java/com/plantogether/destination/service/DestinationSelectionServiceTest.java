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
import com.plantogether.common.grpc.TripMembership;
import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.event.DestinationChosenInternalEvent;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationStatus;
import com.plantogether.destination.repository.DestinationRepository;
import java.time.Instant;
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
class DestinationSelectionServiceTest {

  @Mock private DestinationRepository repository;
  @Mock private TripClient tripClient;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private DestinationSelectionService service;

  private UUID tripId;
  private UUID destinationId;
  private UUID memberUuid;
  private String memberId;
  private String deviceId;
  private Destination destination;

  @BeforeEach
  void setUp() {
    tripId = UUID.randomUUID();
    destinationId = UUID.randomUUID();
    memberUuid = UUID.randomUUID();
    memberId = memberUuid.toString();
    deviceId = UUID.randomUUID().toString();
    destination =
        Destination.builder()
            .id(destinationId)
            .tripId(tripId)
            .name("Lisbon")
            .proposedByTripMemberId(UUID.randomUUID())
            .status(DestinationStatus.PROPOSED)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  private void stubOrganizer() {
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.ORGANIZER, memberId));
  }

  @Test
  void select_success_promotesToChosen_andPublishesEvent() {
    when(repository.findById(destinationId)).thenReturn(Optional.of(destination));
    stubOrganizer();
    when(repository.findByTripIdAndStatus(tripId, DestinationStatus.CHOSEN))
        .thenReturn(Optional.empty());
    when(repository.save(any(Destination.class))).thenAnswer(inv -> inv.getArgument(0));

    DestinationResponse response = service.selectDestination(destinationId, deviceId);

    assertThat(response.getStatus()).isEqualTo(DestinationStatus.CHOSEN);
    assertThat(response.getChosenByMemberId()).isEqualTo(memberUuid);
    assertThat(destination.getStatus()).isEqualTo(DestinationStatus.CHOSEN);

    ArgumentCaptor<DestinationChosenInternalEvent> captor =
        ArgumentCaptor.forClass(DestinationChosenInternalEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().destinationId()).isEqualTo(destinationId);
    assertThat(captor.getValue().previousChosenDestinationId()).isNull();
  }

  @Test
  void select_secondDestination_demotesPrevious_inSameTransaction() {
    Destination previous =
        Destination.builder()
            .id(UUID.randomUUID())
            .tripId(tripId)
            .name("Rome")
            .proposedByTripMemberId(UUID.randomUUID())
            .status(DestinationStatus.CHOSEN)
            .chosenAt(Instant.now())
            .chosenByTripMemberId(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    when(repository.findById(destinationId)).thenReturn(Optional.of(destination));
    stubOrganizer();
    when(repository.findByTripIdAndStatus(tripId, DestinationStatus.CHOSEN))
        .thenReturn(Optional.of(previous));
    when(repository.save(any(Destination.class))).thenAnswer(inv -> inv.getArgument(0));

    service.selectDestination(destinationId, deviceId);

    assertThat(previous.getStatus()).isEqualTo(DestinationStatus.PROPOSED);
    assertThat(previous.getChosenAt()).isNull();
    assertThat(previous.getChosenByTripMemberId()).isNull();
    assertThat(destination.getStatus()).isEqualTo(DestinationStatus.CHOSEN);
    verify(repository).flush();
  }

  @Test
  void select_sameDestination_isIdempotent_noSecondEvent() {
    destination.setStatus(DestinationStatus.CHOSEN);
    destination.setChosenAt(Instant.now());
    destination.setChosenByTripMemberId(memberUuid);
    when(repository.findById(destinationId)).thenReturn(Optional.of(destination));
    stubOrganizer();

    DestinationResponse response = service.selectDestination(destinationId, deviceId);

    assertThat(response.getStatus()).isEqualTo(DestinationStatus.CHOSEN);
    verify(repository, never()).save(any(Destination.class));
    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void select_notFoundDestination_throws404() {
    when(repository.findById(destinationId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.selectDestination(destinationId, deviceId))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void select_nonMember_throwsAccessDenied() {
    when(repository.findById(destinationId)).thenReturn(Optional.of(destination));
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenThrow(new com.plantogether.common.exception.AccessDeniedException("not a member"));

    assertThatThrownBy(() -> service.selectDestination(destinationId, deviceId))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("not a member");
  }

  @Test
  void select_participant_throwsAccessDenied_withOrganizerMessage() {
    when(repository.findById(destinationId)).thenReturn(Optional.of(destination));
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.PARTICIPANT, memberId));

    assertThatThrownBy(() -> service.selectDestination(destinationId, deviceId))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("organizer");
  }

  @Test
  void select_withPreviousChosen_eventCarriesPreviousId() {
    UUID previousId = UUID.randomUUID();
    Destination previous =
        Destination.builder()
            .id(previousId)
            .tripId(tripId)
            .name("Rome")
            .proposedByTripMemberId(UUID.randomUUID())
            .status(DestinationStatus.CHOSEN)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    when(repository.findById(destinationId)).thenReturn(Optional.of(destination));
    stubOrganizer();
    when(repository.findByTripIdAndStatus(tripId, DestinationStatus.CHOSEN))
        .thenReturn(Optional.of(previous));
    when(repository.save(any(Destination.class))).thenAnswer(inv -> inv.getArgument(0));

    service.selectDestination(destinationId, deviceId);

    ArgumentCaptor<DestinationChosenInternalEvent> captor =
        ArgumentCaptor.forClass(DestinationChosenInternalEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().previousChosenDestinationId()).isEqualTo(previousId);
  }

  @Test
  void select_withoutPreviousChosen_eventCarriesNullPrevious() {
    when(repository.findById(destinationId)).thenReturn(Optional.of(destination));
    stubOrganizer();
    when(repository.findByTripIdAndStatus(tripId, DestinationStatus.CHOSEN))
        .thenReturn(Optional.empty());
    when(repository.save(any(Destination.class))).thenAnswer(inv -> inv.getArgument(0));

    service.selectDestination(destinationId, deviceId);

    ArgumentCaptor<DestinationChosenInternalEvent> captor =
        ArgumentCaptor.forClass(DestinationChosenInternalEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().previousChosenDestinationId()).isNull();
  }
}
