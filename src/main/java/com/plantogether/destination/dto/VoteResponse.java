package com.plantogether.destination.dto;

import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteResponse {

  private UUID voterDeviceId;
  private UUID destinationId;
  private Integer rank;
}
