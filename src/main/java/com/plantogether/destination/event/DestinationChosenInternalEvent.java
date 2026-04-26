package com.plantogether.destination.event;

import java.time.Instant;
import java.util.UUID;

public record DestinationChosenInternalEvent(
    UUID tripId,
    UUID destinationId,
    String destinationName,
    UUID chosenByDeviceId,
    Instant chosenAt,
    UUID previousChosenDestinationId) {}
