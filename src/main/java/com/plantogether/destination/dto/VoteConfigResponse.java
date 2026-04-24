package com.plantogether.destination.dto;

import com.plantogether.destination.model.VoteMode;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteConfigResponse {

  private UUID tripId;
  private VoteMode mode;
  private Instant updatedAt;
}
