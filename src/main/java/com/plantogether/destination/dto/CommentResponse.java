package com.plantogether.destination.dto;

import com.plantogether.destination.model.DestinationComment;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

  private UUID id;
  private UUID destinationId;
  private UUID authorMemberId;
  private String authorDisplayName;
  private String content;
  private Instant createdAt;

  public static CommentResponse from(DestinationComment c, String displayName) {
    return CommentResponse.builder()
        .id(c.getId())
        .destinationId(c.getDestinationId())
        .authorMemberId(c.getTripMemberId())
        .authorDisplayName(displayName)
        .content(c.getContent())
        .createdAt(c.getCreatedAt())
        .build();
  }
}
