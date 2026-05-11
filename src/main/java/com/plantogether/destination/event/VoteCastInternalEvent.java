package com.plantogether.destination.event;

import com.plantogether.destination.model.VoteMode;
import java.time.Instant;
import java.util.UUID;

public record VoteCastInternalEvent(
    UUID tripId,
    UUID destinationId,
    UUID tripMemberId,
    VoteMode mode,
    String voteValue,
    Instant occurredAt) {

  public VoteCastInternalEvent(
      UUID tripId, UUID destinationId, UUID tripMemberId, VoteMode mode, String voteValue) {
    this(tripId, destinationId, tripMemberId, mode, voteValue, Instant.now());
  }
}
