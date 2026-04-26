package com.plantogether.destination.exception;

public class DestinationAlreadyChosenException extends RuntimeException {
  public DestinationAlreadyChosenException() {
    super("Destination already chosen for this trip — further changes are not allowed");
  }
}
