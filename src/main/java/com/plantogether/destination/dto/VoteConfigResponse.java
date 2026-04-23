package com.plantogether.destination.dto;

import com.plantogether.destination.model.VoteMode;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteConfigResponse {

    private UUID tripId;
    private VoteMode mode;
    private Instant updatedAt;
}
