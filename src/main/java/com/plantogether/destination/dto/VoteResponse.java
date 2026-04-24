package com.plantogether.destination.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteResponse {

    private UUID voterDeviceId;
    private UUID destinationId;
    private Integer rank;
}
