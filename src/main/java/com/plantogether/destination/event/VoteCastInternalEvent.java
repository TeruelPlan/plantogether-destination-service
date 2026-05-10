package com.plantogether.destination.event;

import com.plantogether.destination.model.VoteMode;
import java.time.Instant;
import java.util.UUID;

public record VoteCastInternalEvent(
    UUID tripId,
    UUID destinationId,
    UUID deviceId,
    UUID tripMemberId,
    VoteMode mode,
    String voteValue,
    Instant occurredAt) {

  public VoteCastInternalEvent(
      UUID tripId,
      UUID destinationId,
      UUID deviceId,
      UUID tripMemberId,
      VoteMode mode,
      String voteValue) {
    this(tripId, destinationId, deviceId, tripMemberId, mode, voteValue, Instant.now());
  }

  // Legacy 5-arg constructor — back-compat for callers that have not yet plumbed tripMemberId.
  public VoteCastInternalEvent(
      UUID tripId, UUID destinationId, UUID deviceId, VoteMode mode, String voteValue) {
    this(tripId, destinationId, deviceId, null, mode, voteValue, Instant.now());
  }
}
