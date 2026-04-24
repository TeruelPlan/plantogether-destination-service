package com.plantogether.destination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.destination.dto.VoteConfigResponse;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.DestinationVoteConfig;
import com.plantogether.destination.model.VoteMode;
import com.plantogether.destination.repository.DestinationVoteConfigRepository;
import com.plantogether.destination.repository.DestinationVoteRepository;
import com.plantogether.trip.grpc.IsMemberResponse;
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

  @Mock private TripGrpcClient tripGrpcClient;

  @Mock private TripLockService tripLockService;

  @InjectMocks private DestinationVoteConfigService service;

  private UUID tripId;
  private String deviceId;

  @BeforeEach
  void setUp() {
    tripId = UUID.randomUUID();
    deviceId = UUID.randomUUID().toString();
  }

  private IsMemberResponse membership(boolean isMember, String role) {
    return IsMemberResponse.newBuilder()
        .setIsMember(isMember)
        .setRole(role == null ? "" : role)
        .build();
  }

  @Test
  void getConfig_absent_returnsDefaultSimple() {
    when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
    when(configRepository.findById(tripId)).thenReturn(Optional.empty());

    VoteConfigResponse response = service.getConfig(tripId, deviceId);

    assertThat(response.getTripId()).isEqualTo(tripId);
    assertThat(response.getMode()).isEqualTo(VoteMode.SIMPLE);
    assertThat(response.getUpdatedAt()).isEqualTo(Instant.EPOCH);
  }

  @Test
  void getConfig_nonMember_throwsAccessDenied() {
    when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(false);

    assertThatThrownBy(() -> service.getConfig(tripId, deviceId))
        .isInstanceOf(AccessDeniedException.class);

    verify(configRepository, never()).findById(any());
  }

  @Test
  void upsertConfig_organizer_persistsAndReturns() {
    when(tripGrpcClient.isMemberWithRole(tripId.toString(), deviceId))
        .thenReturn(membership(true, "ORGANIZER"));
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
    when(tripGrpcClient.isMemberWithRole(tripId.toString(), deviceId))
        .thenReturn(membership(true, "PARTICIPANT"));

    assertThatThrownBy(() -> service.upsertConfig(tripId, deviceId, VoteMode.RANKING))
        .isInstanceOf(AccessDeniedException.class);

    verify(configRepository, never()).save(any());
    verify(voteRepository, never()).nullRanksForTrip(any());
  }

  @Test
  void upsertConfig_switchToSimple_nullsAllRanksForTrip() {
    when(tripGrpcClient.isMemberWithRole(tripId.toString(), deviceId))
        .thenReturn(membership(true, "ORGANIZER"));
    when(configRepository.findById(tripId)).thenReturn(Optional.empty());
    when(configRepository.save(any(DestinationVoteConfig.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.upsertConfig(tripId, deviceId, VoteMode.SIMPLE);

    verify(voteRepository).nullRanksForTrip(tripId);
  }

  @Test
  void upsertConfig_switchToRanking_preservesRanks() {
    when(tripGrpcClient.isMemberWithRole(tripId.toString(), deviceId))
        .thenReturn(membership(true, "ORGANIZER"));
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
    when(tripGrpcClient.isMemberWithRole(tripId.toString(), deviceId))
        .thenReturn(membership(true, "ORGANIZER"));
    when(configRepository.findById(tripId)).thenReturn(Optional.empty());
    when(configRepository.save(any(DestinationVoteConfig.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.upsertConfig(tripId, deviceId, VoteMode.APPROVAL);

    verify(voteRepository).nullRanksForTrip(tripId);
  }
}
