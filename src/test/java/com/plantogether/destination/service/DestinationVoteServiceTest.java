package com.plantogether.destination.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.destination.dto.CastVoteRequest;
import com.plantogether.destination.dto.VoteResponse;
import com.plantogether.destination.event.VoteCastInternalEvent;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationVote;
import com.plantogether.destination.model.DestinationVoteConfig;
import com.plantogether.destination.model.VoteMode;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.destination.repository.DestinationVoteConfigRepository;
import com.plantogether.destination.repository.DestinationVoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DestinationVoteServiceTest {

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private DestinationVoteRepository voteRepository;

    @Mock
    private DestinationVoteConfigRepository configRepository;

    @Mock
    private TripGrpcClient tripGrpcClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TripLockService tripLockService;

    @InjectMocks
    private DestinationVoteService service;

    private UUID tripId;
    private UUID destinationId;
    private String deviceId;
    private UUID deviceUuid;
    private Destination destination;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        destinationId = UUID.randomUUID();
        deviceUuid = UUID.randomUUID();
        deviceId = deviceUuid.toString();
        destination = Destination.builder()
                .id(destinationId).tripId(tripId).name("Paris")
                .proposedBy(UUID.randomUUID())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    private void stubConfig(VoteMode mode) {
        DestinationVoteConfig cfg = DestinationVoteConfig.builder()
                .tripId(tripId).mode(mode).updatedAt(Instant.now()).build();
        when(configRepository.findById(tripId)).thenReturn(Optional.of(cfg));
    }

    private DestinationVote savedVote(Integer rank) {
        DestinationVote v = DestinationVote.builder()
                .id(UUID.randomUUID())
                .destinationId(destinationId)
                .tripId(tripId)
                .deviceId(deviceUuid)
                .rank(rank)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        return v;
    }

    @Test
    void castVote_simple_deletesOtherTripVotes() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
        stubConfig(VoteMode.SIMPLE);
        when(voteRepository.findByDestinationIdAndDeviceId(destinationId, deviceUuid))
                .thenReturn(Optional.empty());
        when(voteRepository.save(any(DestinationVote.class))).thenAnswer(inv -> {
            DestinationVote v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        service.castVote(destinationId, deviceId, CastVoteRequest.builder().build());

        verify(voteRepository).deleteOtherTripVotes(tripId, deviceUuid, destinationId);
    }

    @Test
    void castVote_simple_upsertsRankNull() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
        stubConfig(VoteMode.SIMPLE);
        when(voteRepository.findByDestinationIdAndDeviceId(destinationId, deviceUuid))
                .thenReturn(Optional.empty());
        ArgumentCaptor<DestinationVote> captor = ArgumentCaptor.forClass(DestinationVote.class);
        when(voteRepository.save(captor.capture())).thenAnswer(inv -> {
            DestinationVote v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        VoteResponse response = service.castVote(destinationId, deviceId, CastVoteRequest.builder().build());

        assertThat(captor.getValue().getRank()).isNull();
        assertThat(response.getRank()).isNull();
        assertThat(response.getDestinationId()).isEqualTo(destinationId);
    }

    @Test
    void castVote_approval_multipleDestinations_allowed() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
        stubConfig(VoteMode.APPROVAL);
        when(voteRepository.findByDestinationIdAndDeviceId(destinationId, deviceUuid))
                .thenReturn(Optional.empty());
        when(voteRepository.save(any(DestinationVote.class))).thenAnswer(inv -> {
            DestinationVote v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        service.castVote(destinationId, deviceId, CastVoteRequest.builder().build());

        verify(voteRepository, never()).deleteOtherTripVotes(any(), any(), any());
        verify(voteRepository).save(any(DestinationVote.class));
    }

    @Test
    void castVote_ranking_rankOutOfRange_throws400() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
        stubConfig(VoteMode.RANKING);
        when(destinationRepository.countByTripId(tripId)).thenReturn(3L);

        assertThatThrownBy(() -> service.castVote(destinationId, deviceId,
                CastVoteRequest.builder().rank(5).build()))
                .isInstanceOf(ResponseStatusException.class);

        verify(voteRepository, never()).save(any());
    }

    @Test
    void castVote_ranking_missingRank_throws400() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
        stubConfig(VoteMode.RANKING);

        assertThatThrownBy(() -> service.castVote(destinationId, deviceId,
                CastVoteRequest.builder().build()))
                .isInstanceOf(ResponseStatusException.class);

        verify(voteRepository, never()).save(any());
    }

    @Test
    void castVote_ranking_collision_swapsOtherRowToNull() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
        stubConfig(VoteMode.RANKING);
        when(destinationRepository.countByTripId(tripId)).thenReturn(5L);

        when(voteRepository.clearRankForSwap(tripId, deviceUuid, 2, destinationId)).thenReturn(1);
        when(voteRepository.findByDestinationIdAndDeviceId(destinationId, deviceUuid))
                .thenReturn(Optional.empty());
        when(voteRepository.save(any(DestinationVote.class))).thenAnswer(inv -> {
            DestinationVote v = inv.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return v;
        });

        service.castVote(destinationId, deviceId, CastVoteRequest.builder().rank(2).build());

        verify(voteRepository).clearRankForSwap(tripId, deviceUuid, 2, destinationId);
        ArgumentCaptor<DestinationVote> captor = ArgumentCaptor.forClass(DestinationVote.class);
        verify(voteRepository).save(captor.capture());
        assertThat(captor.getValue().getDestinationId()).isEqualTo(destinationId);
        assertThat(captor.getValue().getRank()).isEqualTo(2);
    }

    @Test
    void castVote_ranking_sameRowReSubmit_isIdempotentUpdate() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
        stubConfig(VoteMode.RANKING);
        when(destinationRepository.countByTripId(tripId)).thenReturn(5L);

        DestinationVote existing = DestinationVote.builder()
                .id(UUID.randomUUID())
                .destinationId(destinationId)
                .tripId(tripId).deviceId(deviceUuid)
                .rank(3)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(voteRepository.clearRankForSwap(tripId, deviceUuid, 3, destinationId)).thenReturn(0);
        when(voteRepository.findByDestinationIdAndDeviceId(destinationId, deviceUuid))
                .thenReturn(Optional.of(existing));
        when(voteRepository.save(any(DestinationVote.class))).thenAnswer(inv -> inv.getArgument(0));

        VoteResponse response = service.castVote(destinationId, deviceId,
                CastVoteRequest.builder().rank(3).build());

        assertThat(response.getRank()).isEqualTo(3);
        verify(voteRepository).save(any(DestinationVote.class));
    }

    @Test
    void castVote_nonMember_throwsAccessDenied() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(false);

        assertThatThrownBy(() -> service.castVote(destinationId, deviceId,
                CastVoteRequest.builder().build()))
                .isInstanceOf(AccessDeniedException.class);

        verify(voteRepository, never()).save(any());
    }

    @Test
    void castVote_unknownDestination_throwsNotFound() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.castVote(destinationId, deviceId,
                CastVoteRequest.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void retractVote_existingRow_deletesAndPublishes() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
        when(voteRepository.deleteByDestinationIdAndDeviceId(destinationId, deviceUuid)).thenReturn(1);
        stubConfig(VoteMode.SIMPLE);

        service.retractVote(destinationId, deviceId);

        verify(voteRepository).deleteByDestinationIdAndDeviceId(destinationId, deviceUuid);
        verify(eventPublisher).publishEvent(any(VoteCastInternalEvent.class));
    }

    @Test
    void retractVote_absentRow_isIdempotentNoEvent() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
        when(voteRepository.deleteByDestinationIdAndDeviceId(destinationId, deviceUuid)).thenReturn(0);

        service.retractVote(destinationId, deviceId);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void castVote_simple_publishesYesEvent() {
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));
        when(tripGrpcClient.isMember(tripId.toString(), deviceId)).thenReturn(true);
        stubConfig(VoteMode.SIMPLE);
        when(voteRepository.findByDestinationIdAndDeviceId(destinationId, deviceUuid))
                .thenReturn(Optional.empty());
        when(voteRepository.save(any(DestinationVote.class))).thenAnswer(inv -> {
            DestinationVote v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        service.castVote(destinationId, deviceId, CastVoteRequest.builder().build());

        ArgumentCaptor<VoteCastInternalEvent> captor =
                ArgumentCaptor.forClass(VoteCastInternalEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().voteValue()).isEqualTo("YES");
        assertThat(captor.getValue().mode()).isEqualTo(VoteMode.SIMPLE);
    }
}
