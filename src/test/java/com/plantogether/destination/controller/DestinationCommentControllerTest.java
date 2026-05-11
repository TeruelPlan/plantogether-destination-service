package com.plantogether.destination.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.destination.dto.CommentResponse;
import com.plantogether.destination.service.DestinationCommentService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DestinationCommentController.class)
@Import(SecurityAutoConfiguration.class)
class DestinationCommentControllerTest {

  private final UUID deviceId = UUID.randomUUID();
  private final UUID destinationId = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DestinationCommentService commentService;

  @AfterEach
  void tearDown() {
    Mockito.reset(commentService);
  }

  private CommentResponse sampleResponse(String content) {
    return CommentResponse.builder()
        .id(UUID.randomUUID())
        .destinationId(destinationId)
        .authorMemberId(deviceId)
        .authorDisplayName("Alice")
        .content(content)
        .createdAt(Instant.parse("2026-04-25T10:00:00Z"))
        .build();
  }

  @Test
  void addComment_returns201_forValidBody() throws Exception {
    when(commentService.addComment(eq(destinationId), eq(deviceId.toString()), any()))
        .thenReturn(sampleResponse("Looks great!"));

    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/comments", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"content\":\"Looks great!\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content").value("Looks great!"))
        .andExpect(jsonPath("$.authorDisplayName").value("Alice"));
  }

  @Test
  void addComment_returns400_forBlankContent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/comments", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"content\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void addComment_returns400_forContentOver2000Chars() throws Exception {
    String longContent = "a".repeat(2001);
    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/comments", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"content\":\"" + longContent + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void addComment_returns400_forMalformedJson() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/comments", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"content\":"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void addComment_returns403_whenServiceThrowsAccessDenied() throws Exception {
    when(commentService.addComment(eq(destinationId), eq(deviceId.toString()), any()))
        .thenThrow(new AccessDeniedException("Device is not a member of this trip"));

    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/comments", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"content\":\"hello\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void addComment_returns404_whenServiceThrowsResourceNotFound() throws Exception {
    when(commentService.addComment(eq(destinationId), eq(deviceId.toString()), any()))
        .thenThrow(
            new ResourceNotFoundException("Destination not found with id: " + destinationId));

    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/comments", destinationId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"content\":\"hello\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void listComments_returns200_withSortedList() throws Exception {
    when(commentService.listComments(destinationId, deviceId.toString()))
        .thenReturn(List.of(sampleResponse("first"), sampleResponse("second")));

    mockMvc
        .perform(
            get("/api/v1/destinations/{destinationId}/comments", destinationId)
                .header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].content").value("first"))
        .andExpect(jsonPath("$[1].content").value("second"));
  }

  @Test
  void listComments_returns403_whenServiceThrowsAccessDenied() throws Exception {
    when(commentService.listComments(destinationId, deviceId.toString()))
        .thenThrow(new AccessDeniedException("Device is not a member of this trip"));

    mockMvc
        .perform(
            get("/api/v1/destinations/{destinationId}/comments", destinationId)
                .header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isForbidden());
  }
}
