package com.plantogether.destination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.grpc.Role;
import com.plantogether.common.grpc.TripClient;
import com.plantogether.common.grpc.TripMembership;
import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.dto.ProposeDestinationRequest;
import com.plantogether.destination.exception.DestinationAlreadyChosenException;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationStatus;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.destination.repository.DestinationVoteRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DestinationServiceTest {

  @Mock private DestinationRepository repository;

  @Mock private DestinationVoteRepository voteRepository;

  @Mock private TripClient tripClient;

  @InjectMocks private DestinationService service;

  private UUID tripId;
  private String deviceId;

  @BeforeEach
  void setUp() {
    tripId = UUID.randomUUID();
    deviceId = UUID.randomUUID().toString();
  }

  private ProposeDestinationRequest validRequest() {
    return ProposeDestinationRequest.builder()
        .name("Paris")
        .description("City of lights")
        .estimatedBudget(new BigDecimal("1200.00"))
        .currency("EUR")
        .externalUrl("https://example.com/paris")
        .build();
  }

  @Test
  void propose_member_savesAndReturnsResponse() {
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.PARTICIPANT));
    when(repository.save(any(Destination.class)))
        .thenAnswer(
            inv -> {
              Destination d = inv.getArgument(0);
              d.setId(UUID.randomUUID());
              d.setCreatedAt(Instant.now());
              d.setUpdatedAt(Instant.now());
              return d;
            });

    DestinationResponse response = service.proposeDestination(tripId, deviceId, validRequest());

    assertThat(response.getName()).isEqualTo("Paris");
    assertThat(response.getTripId()).isEqualTo(tripId);
    assertThat(response.getProposedByDeviceId()).isEqualTo(UUID.fromString(deviceId));
    assertThat(response.getVotes().getTotalVotes()).isZero();
    assertThat(response.getVotes().getRankVotes()).isEmpty();
    verify(repository).save(any(Destination.class));
  }

  @Test
  void propose_nonMember_throwsAccessDenied() {
    doThrow(new AccessDeniedException("Device is not a member of this trip"))
        .when(tripClient)
        .requireMembership(tripId.toString(), deviceId);

    assertThatThrownBy(() -> service.proposeDestination(tripId, deviceId, validRequest()))
        .isInstanceOf(AccessDeniedException.class);

    verify(repository, never()).save(any());
  }

  @Test
  void list_member_returnsOrderedByCreatedAtDesc() {
    Destination d1 =
        Destination.builder()
            .id(UUID.randomUUID())
            .tripId(tripId)
            .name("A")
            .proposedBy(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    Destination d2 =
        Destination.builder()
            .id(UUID.randomUUID())
            .tripId(tripId)
            .name("B")
            .proposedBy(UUID.randomUUID())
            .createdAt(Instant.now().minusSeconds(60))
            .updatedAt(Instant.now().minusSeconds(60))
            .build();
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.PARTICIPANT));
    when(repository.findByTripIdOrderByCreatedAtDesc(tripId)).thenReturn(List.of(d1, d2));
    when(voteRepository.findByDestinationIdIn(any())).thenReturn(List.of());

    List<DestinationResponse> result = service.listDestinations(tripId, deviceId);

    assertThat(result).extracting(DestinationResponse::getName).containsExactly("A", "B");
  }

  @Test
  void propose_whenTripHasChosen_throws409() {
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.PARTICIPANT));
    when(repository.findByTripIdAndStatus(tripId, DestinationStatus.CHOSEN))
        .thenReturn(Optional.of(new Destination()));

    assertThatThrownBy(() -> service.proposeDestination(tripId, deviceId, validRequest()))
        .isInstanceOf(DestinationAlreadyChosenException.class);

    verify(repository, never()).save(any());
  }

  @Test
  void list_nonMember_throwsAccessDenied() {
    doThrow(new AccessDeniedException("Device is not a member of this trip"))
        .when(tripClient)
        .requireMembership(eq(tripId.toString()), eq(deviceId));

    assertThatThrownBy(() -> service.listDestinations(tripId, deviceId))
        .isInstanceOf(AccessDeniedException.class);

    verify(repository, never()).findByTripIdOrderByCreatedAtDesc(any());
  }
}
