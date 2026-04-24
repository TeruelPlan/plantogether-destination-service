package com.plantogether.destination.event;

import com.plantogether.destination.model.VoteMode;
import java.time.Instant;
import java.util.UUID;

public record VoteCastInternalEvent(
    UUID tripId,
    UUID destinationId,
    UUID deviceId,
    VoteMode mode,
    String voteValue,
    Instant occurredAt) {
  public VoteCastInternalEvent(
      UUID tripId, UUID destinationId, UUID deviceId, VoteMode mode, String voteValue) {
    this(tripId, destinationId, deviceId, mode, voteValue, Instant.now());
  }
}
