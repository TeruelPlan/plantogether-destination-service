package com.plantogether.destination.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.exception.DestinationAlreadyChosenException;
import com.plantogether.destination.model.DestinationStatus;
import com.plantogether.destination.service.DestinationSelectionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DestinationSelectionController.class)
@Import(SecurityAutoConfiguration.class)
class DestinationSelectionControllerTest {

  private final UUID deviceId = UUID.randomUUID();
  private final UUID destinationId = UUID.randomUUID();
  private final UUID tripId = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DestinationSelectionService selectionService;

  @AfterEach
  void tearDown() {
    Mockito.reset(selectionService);
  }

  private DestinationResponse responseChosen() {
    return DestinationResponse.builder()
        .id(destinationId)
        .tripId(tripId)
        .name("Lisbon")
        .proposedByDeviceId(UUID.randomUUID())
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .status(DestinationStatus.CHOSEN)
        .chosenAt(Instant.now())
        .chosenByDeviceId(deviceId)
        .build();
  }

  @Test
  void select_returns200_forOrganizer() throws Exception {
    when(selectionService.selectDestination(eq(destinationId), eq(deviceId.toString())))
        .thenReturn(responseChosen());

    mockMvc
        .perform(
            patch("/api/v1/destinations/{destinationId}/select", destinationId)
                .header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CHOSEN"))
        .andExpect(jsonPath("$.chosenByDeviceId").value(deviceId.toString()));
  }

  @Test
  void select_returns403_forParticipant() throws Exception {
    when(selectionService.selectDestination(eq(destinationId), eq(deviceId.toString())))
        .thenThrow(new AccessDeniedException("Only the trip organizer can select a destination"));

    mockMvc
        .perform(
            patch("/api/v1/destinations/{destinationId}/select", destinationId)
                .header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Only the trip organizer can select a destination"));
  }

  @Test
  void select_returns403_forNonMember() throws Exception {
    when(selectionService.selectDestination(eq(destinationId), eq(deviceId.toString())))
        .thenThrow(new AccessDeniedException("Device is not a member of this trip"));

    mockMvc
        .perform(
            patch("/api/v1/destinations/{destinationId}/select", destinationId)
                .header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Device is not a member of this trip"));
  }

  @Test
  void select_returns404_forUnknownDestination() throws Exception {
    when(selectionService.selectDestination(eq(destinationId), eq(deviceId.toString())))
        .thenThrow(new ResourceNotFoundException("Destination not found: " + destinationId));

    mockMvc
        .perform(
            patch("/api/v1/destinations/{destinationId}/select", destinationId)
                .header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  void select_returns409_whenAlreadyChosenSurfacesFromService() throws Exception {
    when(selectionService.selectDestination(eq(destinationId), eq(deviceId.toString())))
        .thenThrow(new DestinationAlreadyChosenException());

    mockMvc
        .perform(
            patch("/api/v1/destinations/{destinationId}/select", destinationId)
                .header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isConflict());
  }
}
