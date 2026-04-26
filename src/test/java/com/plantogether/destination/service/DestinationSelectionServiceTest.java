package com.plantogether.destination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.event.DestinationChosenInternalEvent;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationStatus;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.trip.grpc.IsMemberResponse;
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
  @Mock private TripGrpcClient tripGrpcClient;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private DestinationSelectionService service;

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
            .name("Lisbon")
            .proposedBy(UUID.randomUUID())
            .status(DestinationStatus.PROPOSED)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  private IsMemberResponse membership(boolean isMember, String role) {
    return IsMemberResponse.newBuilder()
        .setIsMember(isMember)
        .setRole(role == null ? "" : role)
        .build();
  }

  private void stubOrganizer() {
    when(tripGrpcClient.isMemberWithRole(tripId.toString(), deviceId))
        .thenReturn(membership(true, "ORGANIZER"));
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
    assertThat(response.getChosenByDeviceId()).isEqualTo(deviceUuid);
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
            .proposedBy(UUID.randomUUID())
            .status(DestinationStatus.CHOSEN)
            .chosenAt(Instant.now())
            .chosenBy(UUID.randomUUID())
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
    assertThat(previous.getChosenBy()).isNull();
    assertThat(destination.getStatus()).isEqualTo(DestinationStatus.CHOSEN);
    verify(repository).flush();
  }

  @Test
  void select_sameDestination_isIdempotent_noSecondEvent() {
    destination.setStatus(DestinationStatus.CHOSEN);
    destination.setChosenAt(Instant.now());
    destination.setChosenBy(deviceUuid);
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
    when(tripGrpcClient.isMemberWithRole(tripId.toString(), deviceId))
        .thenReturn(membership(false, ""));

    assertThatThrownBy(() -> service.selectDestination(destinationId, deviceId))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("not a member");
  }

  @Test
  void select_participant_throwsAccessDenied_withOrganizerMessage() {
    when(repository.findById(destinationId)).thenReturn(Optional.of(destination));
    when(tripGrpcClient.isMemberWithRole(tripId.toString(), deviceId))
        .thenReturn(membership(true, "PARTICIPANT"));

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
            .proposedBy(UUID.randomUUID())
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
