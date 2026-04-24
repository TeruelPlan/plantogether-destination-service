package com.plantogether.destination.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.destination.dto.VoteResponse;
import com.plantogether.destination.service.DestinationVoteService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DestinationVoteController.class)
@Import(SecurityAutoConfiguration.class)
class DestinationVoteControllerTest {

  private final UUID deviceId = UUID.randomUUID();
  private final UUID destinationId = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DestinationVoteService voteService;

  @AfterEach
  void tearDown() {
    Mockito.reset(voteService);
  }

  @Test
  void castVote_returns200_forValidSimple() throws Exception {
    when(voteService.castVote(eq(destinationId), eq(deviceId.toString()), any()))
        .thenReturn(
            VoteResponse.builder()
                .voterDeviceId(deviceId)
                .destinationId(destinationId)
                .rank(null)
                .build());

    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/vote", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.destinationId").value(destinationId.toString()));
  }

  @Test
  void castVote_returns400_forRankingWithoutRank() throws Exception {
    when(voteService.castVote(eq(destinationId), eq(deviceId.toString()), any()))
        .thenThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "rank is required in RANKING mode"));

    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/vote", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void castVote_returns400_forRankingRankBelow1() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/vote", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"rank\": 0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void castVote_returns403_forNonMember() throws Exception {
    when(voteService.castVote(eq(destinationId), eq(deviceId.toString()), any()))
        .thenThrow(new AccessDeniedException("Not a member"));

    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/vote", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void castVote_returns404_forUnknownDestination() throws Exception {
    when(voteService.castVote(eq(destinationId), eq(deviceId.toString()), any()))
        .thenThrow(new ResourceNotFoundException("Destination not found"));

    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/vote", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void retractVote_returns204_whenIdempotent() throws Exception {
    doNothing().when(voteService).retractVote(destinationId, deviceId.toString());

    mockMvc
        .perform(
            delete("/api/v1/destinations/{destinationId}/vote", destinationId)
                .header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isNoContent());
  }

  @Test
  void retractVote_returns403_forNonMember() throws Exception {
    doThrow(new AccessDeniedException("Not a member"))
        .when(voteService)
        .retractVote(destinationId, deviceId.toString());

    mockMvc
        .perform(
            delete("/api/v1/destinations/{destinationId}/vote", destinationId)
                .header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isForbidden());
  }
}
