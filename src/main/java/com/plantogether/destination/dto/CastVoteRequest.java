package com.plantogether.destination.dto;

import jakarta.validation.constraints.Min;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CastVoteRequest {

    @Min(1)
    private Integer rank;
}
