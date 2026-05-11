package com.plantogether.destination.service;

import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.common.grpc.TripClient;
import com.plantogether.destination.dto.CastVoteRequest;
import com.plantogether.destination.dto.VoteResponse;
import com.plantogether.destination.event.VoteCastInternalEvent;
import com.plantogether.destination.exception.DestinationAlreadyChosenException;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationStatus;
import com.plantogether.destination.model.DestinationVote;
import com.plantogether.destination.model.VoteMode;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.destination.repository.DestinationVoteConfigRepository;
import com.plantogether.destination.repository.DestinationVoteRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DestinationVoteService {

  private final DestinationRepository destinationRepository;
  private final DestinationVoteRepository voteRepository;
  private final DestinationVoteConfigRepository configRepository;
  private final TripClient tripClient;
  private final ApplicationEventPublisher eventPublisher;
  private final TripLockService tripLockService;

  @Transactional
  public VoteResponse castVote(UUID destinationId, String deviceId, CastVoteRequest req) {
    Destination destination =
        destinationRepository
            .findById(destinationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Destination not found: " + destinationId));

    var membership = tripClient.requireMembership(destination.getTripId().toString(), deviceId);
    UUID memberUuid = UUID.fromString(membership.tripMemberId());

    UUID tripId = destination.getTripId();
    requireNotChosen(tripId);

    // Serialize mode-aware writes: SIMPLE "one vote per trip" and RANKING "unique rank per
    // member" invariants span multiple rows and cannot be expressed as pure DB constraints
    // because APPROVAL shares the same table with rank IS NULL.
    tripLockService.lock(tripId);

    VoteMode mode = resolveMode(tripId);

    DestinationVote vote;
    switch (mode) {
      case SIMPLE -> {
        vote = upsert(destinationId, tripId, memberUuid, null);
        voteRepository.deleteOtherTripVotes(tripId, memberUuid, destinationId);
      }
      case APPROVAL -> {
        vote = upsert(destinationId, tripId, memberUuid, null);
      }
      case RANKING -> {
        if (req == null || req.getRank() == null) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "rank is required in RANKING mode");
        }
        int n = (int) destinationRepository.countByTripId(tripId);
        if (req.getRank() > n) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "rank must be <= number of destinations (" + n + ")");
        }
        voteRepository.clearRankForSwap(tripId, memberUuid, req.getRank(), destinationId);
        vote = upsert(destinationId, tripId, memberUuid, req.getRank());
      }
      default -> throw new IllegalStateException("Unknown vote mode: " + mode);
    }

    String voteValue =
        switch (mode) {
          case SIMPLE, APPROVAL -> "YES";
          case RANKING -> String.valueOf(vote.getRank());
        };

    eventPublisher.publishEvent(
        new VoteCastInternalEvent(tripId, destinationId, memberUuid, mode, voteValue));

    return VoteResponse.builder()
        .voterMemberId(memberUuid)
        .destinationId(destinationId)
        .rank(vote.getRank())
        .build();
  }

  @Transactional
  public void retractVote(UUID destinationId, String deviceId) {
    Destination destination =
        destinationRepository
            .findById(destinationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Destination not found: " + destinationId));

    var membership = tripClient.requireMembership(destination.getTripId().toString(), deviceId);
    UUID memberUuid = UUID.fromString(membership.tripMemberId());

    requireNotChosen(destination.getTripId());

    int deleted = voteRepository.deleteByDestinationIdAndTripMemberId(destinationId, memberUuid);
    if (deleted == 0) {
      return;
    }

    VoteMode mode = resolveMode(destination.getTripId());

    eventPublisher.publishEvent(
        new VoteCastInternalEvent(
            destination.getTripId(), destinationId, memberUuid, mode, "RETRACTED"));
  }

  private VoteMode resolveMode(UUID tripId) {
    return configRepository.findById(tripId).map(c -> c.getMode()).orElse(VoteMode.SIMPLE);
  }

  private void requireNotChosen(UUID tripId) {
    if (destinationRepository.findByTripIdAndStatus(tripId, DestinationStatus.CHOSEN).isPresent()) {
      throw new DestinationAlreadyChosenException();
    }
  }

  private DestinationVote upsert(UUID destinationId, UUID tripId, UUID tripMemberId, Integer rank) {
    DestinationVote vote =
        voteRepository
            .findByDestinationIdAndTripMemberId(destinationId, tripMemberId)
            .orElse(
                DestinationVote.builder()
                    .destinationId(destinationId)
                    .tripId(tripId)
                    .tripMemberId(tripMemberId)
                    .build());
    vote.setRank(rank);
    return voteRepository.save(vote);
  }
}
