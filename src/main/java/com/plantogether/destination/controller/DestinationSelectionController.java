package com.plantogether.destination.controller;

import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.service.DestinationSelectionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/destinations/{destinationId}")
@RequiredArgsConstructor
public class DestinationSelectionController {

  private final DestinationSelectionService selectionService;

  @PatchMapping("/select")
  @ResponseStatus(HttpStatus.OK)
  public DestinationResponse select(
      Authentication authentication, @PathVariable UUID destinationId) {
    return selectionService.selectDestination(destinationId, authentication.getName());
  }
}
