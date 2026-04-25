package com.plantogether.destination.event;

import java.time.Instant;
import java.util.UUID;

public record DestinationCommentAddedInternalEvent(
    UUID tripId, UUID destinationId, UUID commentId, UUID authorDeviceId, Instant occurredAt) {}
