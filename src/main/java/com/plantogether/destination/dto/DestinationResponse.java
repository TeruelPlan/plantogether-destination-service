package com.plantogether.destination.dto;

import com.plantogether.destination.model.Destination;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DestinationResponse {

    private UUID id;
    private UUID tripId;
    private String name;
    private String description;
    private String imageKey;
    private BigDecimal estimatedBudget;
    private String currency;
    private String externalUrl;
    private UUID proposedByDeviceId;
    private Instant createdAt;
    private Instant updatedAt;
    private VoteAggregate votes;

    public static DestinationResponse from(Destination entity) {
        return DestinationResponse.builder()
                .id(entity.getId())
                .tripId(entity.getTripId())
                .name(entity.getName())
                .description(entity.getDescription())
                .imageKey(entity.getImageKey())
                .estimatedBudget(entity.getEstimatedBudget())
                .currency(entity.getCurrency())
                .externalUrl(entity.getExternalUrl())
                .proposedByDeviceId(entity.getProposedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .votes(VoteAggregate.empty())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoteAggregate {
        private int totalVotes;
        private Map<Integer, Integer> rankVotes;

        public static VoteAggregate empty() {
            return VoteAggregate.builder()
                    .totalVotes(0)
                    .rankVotes(Map.of())
                    .build();
        }
    }
}
