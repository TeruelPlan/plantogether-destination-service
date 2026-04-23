package com.plantogether.destination.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.destination.dto.CastVoteRequest;
import com.plantogether.destination.dto.VoteResponse;
import com.plantogether.destination.event.VoteCastInternalEvent;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationVote;
import com.plantogether.destination.model.VoteMode;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.destination.repository.DestinationVoteConfigRepository;
import com.plantogether.destination.repository.DestinationVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DestinationVoteService {

    private final DestinationRepository destinationRepository;
    private final DestinationVoteRepository voteRepository;
    private final DestinationVoteConfigRepository configRepository;
    private final TripGrpcClient tripGrpcClient;
    private final ApplicationEventPublisher eventPublisher;
    private final TripLockService tripLockService;

    @Transactional
    public VoteResponse castVote(UUID destinationId, String deviceId, CastVoteRequest req) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found: " + destinationId));

        if (!tripGrpcClient.isMember(destination.getTripId().toString(), deviceId)) {
            throw new AccessDeniedException("Device is not a member of this trip");
        }

        UUID tripId = destination.getTripId();
        UUID deviceUuid = UUID.fromString(deviceId);

        // Serialize mode-aware writes: SIMPLE "one vote per trip" and RANKING "unique rank per
        // device" invariants span multiple rows and cannot be expressed as pure DB constraints
        // because APPROVAL shares the same table with rank IS NULL.
        tripLockService.lock(tripId);

        VoteMode mode = configRepository.findById(tripId)
                .map(c -> c.getMode())
                .orElse(VoteMode.SIMPLE);

        DestinationVote vote;
        switch (mode) {
            case SIMPLE -> {
                vote = upsert(destinationId, tripId, deviceUuid, null);
                voteRepository.deleteOtherTripVotes(tripId, deviceUuid, destinationId);
            }
            case APPROVAL -> {
                vote = upsert(destinationId, tripId, deviceUuid, null);
            }
            case RANKING -> {
                if (req == null || req.getRank() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rank is required in RANKING mode");
                }
                int n = (int) destinationRepository.countByTripId(tripId);
                if (req.getRank() > n) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "rank must be <= number of destinations (" + n + ")");
                }
                voteRepository.clearRankForSwap(tripId, deviceUuid, req.getRank(), destinationId);
                vote = upsert(destinationId, tripId, deviceUuid, req.getRank());
            }
            default -> throw new IllegalStateException("Unknown vote mode: " + mode);
        }

        String voteValue = switch (mode) {
            case SIMPLE, APPROVAL -> "YES";
            case RANKING -> String.valueOf(vote.getRank());
        };

        eventPublisher.publishEvent(new VoteCastInternalEvent(
                tripId, destinationId, deviceUuid, mode, voteValue));

        return VoteResponse.builder()
                .voterDeviceId(deviceUuid)
                .destinationId(destinationId)
                .rank(vote.getRank())
                .build();
    }

    @Transactional
    public void retractVote(UUID destinationId, String deviceId) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found: " + destinationId));

        if (!tripGrpcClient.isMember(destination.getTripId().toString(), deviceId)) {
            throw new AccessDeniedException("Device is not a member of this trip");
        }

        UUID deviceUuid = UUID.fromString(deviceId);
        // Retract is a single-row delete gated by the (destination_id, device_id) unique
        // constraint — no cross-row invariant, no lock needed.
        int deleted = voteRepository.deleteByDestinationIdAndDeviceId(destinationId, deviceUuid);
        if (deleted == 0) {
            return;
        }

        VoteMode mode = configRepository.findById(destination.getTripId())
                .map(c -> c.getMode())
                .orElse(VoteMode.SIMPLE);

        eventPublisher.publishEvent(new VoteCastInternalEvent(
                destination.getTripId(), destinationId, deviceUuid, mode, "RETRACTED"));
    }

    private DestinationVote upsert(UUID destinationId, UUID tripId, UUID deviceId, Integer rank) {
        DestinationVote vote = voteRepository.findByDestinationIdAndDeviceId(destinationId, deviceId)
                .orElse(DestinationVote.builder()
                        .destinationId(destinationId)
                        .tripId(tripId)
                        .deviceId(deviceId)
                        .build());
        vote.setRank(rank);
        return voteRepository.save(vote);
    }
}
