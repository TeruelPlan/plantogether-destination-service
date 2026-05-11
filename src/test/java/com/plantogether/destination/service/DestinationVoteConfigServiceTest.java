package com.plantogether.destination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.grpc.Role;
import com.plantogether.common.grpc.TripClient;
import com.plantogether.common.grpc.TripMembership;
import com.plantogether.destination.dto.VoteConfigResponse;
import com.plantogether.destination.exception.DestinationAlreadyChosenException;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationStatus;
import com.plantogether.destination.model.DestinationVoteConfig;
import com.plantogether.destination.model.VoteMode;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.destination.repository.DestinationVoteConfigRepository;
import com.plantogether.destination.repository.DestinationVoteRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DestinationVoteConfigServiceTest {

  @Mock private DestinationVoteConfigRepository configRepository;

  @Mock private DestinationVoteRepository voteRepository;

  @Mock private DestinationRepository destinationRepository;

  @Mock private TripClient tripClient;

  @Mock private TripLockService tripLockService;

  @InjectMocks private DestinationVoteConfigService service;

  private static final String MEMBER_ID = UUID.randomUUID().toString();

  private UUID tripId;
  private String deviceId;

  @BeforeEach
  void setUp() {
    tripId = UUID.randomUUID();
    deviceId = UUID.randomUUID().toString();
  }

  @Test
  void getConfig_absent_returnsDefaultSimple() {
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.PARTICIPANT, MEMBER_ID));
    when(configRepository.findById(tripId)).thenReturn(Optional.empty());

    VoteConfigResponse response = service.getConfig(tripId, deviceId);

    assertThat(response.getTripId()).isEqualTo(tripId);
    assertThat(response.getMode()).isEqualTo(VoteMode.SIMPLE);
    assertThat(response.getUpdatedAt()).isEqualTo(Instant.EPOCH);
  }

  @Test
  void getConfig_nonMember_throwsAccessDenied() {
    doThrow(new AccessDeniedException("Device is not a member of this trip"))
        .when(tripClient)
        .requireMembership(tripId.toString(), deviceId);

    assertThatThrownBy(() -> service.getConfig(tripId, deviceId))
        .isInstanceOf(AccessDeniedException.class);

    verify(configRepository, never()).findById(any());
  }

  @Test
  void upsertConfig_organizer_persistsAndReturns() {
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.ORGANIZER, MEMBER_ID));
    when(configRepository.findById(tripId)).thenReturn(Optional.empty());
    when(configRepository.save(any(DestinationVoteConfig.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    VoteConfigResponse response = service.upsertConfig(tripId, deviceId, VoteMode.RANKING);

    assertThat(response.getMode()).isEqualTo(VoteMode.RANKING);
    assertThat(response.getTripId()).isEqualTo(tripId);
    assertThat(response.getUpdatedAt()).isNotNull();
    verify(configRepository).save(any(DestinationVoteConfig.class));
    // RANKING does not null ranks
    verify(voteRepository, never()).nullRanksForTrip(any());
  }

  @Test
  void upsertConfig_participant_throwsAccessDenied() {
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.PARTICIPANT, MEMBER_ID));

    assertThatThrownBy(() -> service.upsertConfig(tripId, deviceId, VoteMode.RANKING))
        .isInstanceOf(AccessDeniedException.class);

    verify(configRepository, never()).save(any());
    verify(voteRepository, never()).nullRanksForTrip(any());
  }

  @Test
  void upsertConfig_switchToSimple_nullsAllRanksForTrip() {
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.ORGANIZER, MEMBER_ID));
    when(configRepository.findById(tripId)).thenReturn(Optional.empty());
    when(configRepository.save(any(DestinationVoteConfig.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.upsertConfig(tripId, deviceId, VoteMode.SIMPLE);

    verify(voteRepository).nullRanksForTrip(tripId);
  }

  @Test
  void upsertConfig_switchToRanking_preservesRanks() {
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.ORGANIZER, MEMBER_ID));
    DestinationVoteConfig existing =
        DestinationVoteConfig.builder()
            .tripId(tripId)
            .mode(VoteMode.SIMPLE)
            .updatedAt(Instant.now())
            .build();
    when(configRepository.findById(tripId)).thenReturn(Optional.of(existing));
    when(configRepository.save(any(DestinationVoteConfig.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.upsertConfig(tripId, deviceId, VoteMode.RANKING);

    verify(voteRepository, never()).nullRanksForTrip(any());
  }

  @Test
  void upsertConfig_switchToApproval_nullsAllRanksForTrip() {
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.ORGANIZER, MEMBER_ID));
    when(configRepository.findById(tripId)).thenReturn(Optional.empty());
    when(configRepository.save(any(DestinationVoteConfig.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.upsertConfig(tripId, deviceId, VoteMode.APPROVAL);

    verify(voteRepository).nullRanksForTrip(tripId);
  }

  @Test
  void upsertConfig_whenTripHasChosen_throws409() {
    when(tripClient.requireMembership(tripId.toString(), deviceId))
        .thenReturn(new TripMembership(true, Role.ORGANIZER, MEMBER_ID));
    when(destinationRepository.findByTripIdAndStatus(tripId, DestinationStatus.CHOSEN))
        .thenReturn(Optional.of(new Destination()));

    assertThatThrownBy(() -> service.upsertConfig(tripId, deviceId, VoteMode.RANKING))
        .isInstanceOf(DestinationAlreadyChosenException.class);

    verify(configRepository, never()).save(any());
  }
}
