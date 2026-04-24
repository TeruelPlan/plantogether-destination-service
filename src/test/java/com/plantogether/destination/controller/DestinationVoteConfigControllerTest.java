package com.plantogether.destination.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.destination.dto.VoteConfigResponse;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.VoteMode;
import com.plantogether.destination.service.DestinationService;
import com.plantogether.destination.service.DestinationVoteConfigService;
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

@WebMvcTest(DestinationController.class)
@Import(SecurityAutoConfiguration.class)
class DestinationVoteConfigControllerTest {

  private final UUID deviceId = UUID.randomUUID();
  private final UUID tripId = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DestinationService destinationService;

  @MockitoBean private DestinationVoteConfigService voteConfigService;

  @MockitoBean private TripGrpcClient tripGrpcClient;

  @AfterEach
  void tearDown() {
    Mockito.reset(destinationService, voteConfigService, tripGrpcClient);
  }

  @Test
  void putVoteConfig_returns200_forOrganizer() throws Exception {
    when(voteConfigService.upsertConfig(eq(tripId), eq(deviceId.toString()), eq(VoteMode.RANKING)))
        .thenReturn(
            VoteConfigResponse.builder()
                .tripId(tripId)
                .mode(VoteMode.RANKING)
                .updatedAt(Instant.now())
                .build());

    mockMvc
        .perform(
            put("/api/v1/trips/{tripId}/destinations/vote-config", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"mode\": \"RANKING\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("RANKING"));
  }

  @Test
  void putVoteConfig_returns403_forParticipant() throws Exception {
    when(voteConfigService.upsertConfig(eq(tripId), eq(deviceId.toString()), any()))
        .thenThrow(
            new AccessDeniedException("Only the trip organizer can configure the vote mode"));

    mockMvc
        .perform(
            put("/api/v1/trips/{tripId}/destinations/vote-config", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"mode\": \"SIMPLE\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void putVoteConfig_returns400_forInvalidMode() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/trips/{tripId}/destinations/vote-config", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"mode\": \"RANDOM\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getVoteConfig_returns200_withDefault_whenNoRow() throws Exception {
    when(voteConfigService.getConfig(eq(tripId), eq(deviceId.toString())))
        .thenReturn(
            VoteConfigResponse.builder()
                .tripId(tripId)
                .mode(VoteMode.SIMPLE)
                .updatedAt(Instant.EPOCH)
                .build());

    mockMvc
        .perform(
            get("/api/v1/trips/{tripId}/destinations/vote-config", tripId)
                .header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("SIMPLE"))
        .andExpect(jsonPath("$.tripId").value(tripId.toString()));
  }
}
