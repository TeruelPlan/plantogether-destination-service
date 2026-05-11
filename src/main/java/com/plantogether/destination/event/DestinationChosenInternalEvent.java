package com.plantogether.destination.event;

import java.time.Instant;
import java.util.UUID;

public record DestinationChosenInternalEvent(
    UUID tripId,
    UUID destinationId,
    String destinationName,
    String chosenByDeviceId,
    Instant chosenAt,
    UUID previousChosenDestinationId) {}
