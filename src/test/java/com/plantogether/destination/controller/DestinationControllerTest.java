package com.plantogether.destination.controller;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.service.DestinationService;
import com.plantogether.destination.service.DestinationVoteConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DestinationController.class)
@Import(SecurityAutoConfiguration.class)
class DestinationControllerTest {

    private final UUID deviceId = UUID.randomUUID();
    private final UUID tripId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DestinationService destinationService;

    @MockitoBean
    private TripGrpcClient tripGrpcClient;

    @MockitoBean
    private DestinationVoteConfigService destinationVoteConfigService;

    @AfterEach
    void tearDown() {
        Mockito.reset(destinationService, tripGrpcClient, destinationVoteConfigService);
    }

    private String validBody() {
        return """
                {
                  "name": "Paris",
                  "description": "City of lights",
                  "estimatedBudget": 1200.00,
                  "currency": "EUR",
                  "externalUrl": "https://example.com"
                }
                """;
    }

    private DestinationResponse sample() {
        return DestinationResponse.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .name("Paris")
                .description("City of lights")
                .estimatedBudget(new BigDecimal("1200.00"))
                .currency("EUR")
                .externalUrl("https://example.com")
                .proposedByDeviceId(deviceId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .votes(DestinationResponse.VoteAggregate.builder()
                        .totalVotes(0)
                        .rankVotes(Map.of())
                        .build())
                .build();
    }

    @Test
    void propose_returns201_withValidBody() throws Exception {
        when(destinationService.proposeDestination(eq(tripId), eq(deviceId.toString()), any()))
                .thenReturn(sample());

        mockMvc.perform(post("/api/v1/trips/{tripId}/destinations", tripId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Paris"))
                .andExpect(jsonPath("$.votes.totalVotes").value(0));
    }

    @Test
    void propose_returns400_withBlankName() throws Exception {
        String body = """
                {
                  "name": "  ",
                  "description": "x"
                }
                """;

        mockMvc.perform(post("/api/v1/trips/{tripId}/destinations", tripId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void propose_returns400_withInvalidCurrency() throws Exception {
        String body = """
                {
                  "name": "Paris",
                  "currency": "euro"
                }
                """;

        mockMvc.perform(post("/api/v1/trips/{tripId}/destinations", tripId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void propose_returns403_forNonMember() throws Exception {
        when(destinationService.proposeDestination(eq(tripId), eq(deviceId.toString()), any()))
                .thenThrow(new AccessDeniedException("Not a member"));

        mockMvc.perform(post("/api/v1/trips/{tripId}/destinations", tripId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_returns200_withDestinations() throws Exception {
        when(destinationService.listDestinations(eq(tripId), eq(deviceId.toString())))
                .thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/v1/trips/{tripId}/destinations", tripId)
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Paris"));
    }

    @Test
    void list_returns403_forNonMember() throws Exception {
        when(destinationService.listDestinations(eq(tripId), eq(deviceId.toString())))
                .thenThrow(new AccessDeniedException("Not a member"));

        mockMvc.perform(get("/api/v1/trips/{tripId}/destinations", tripId)
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isForbidden());
    }
}
