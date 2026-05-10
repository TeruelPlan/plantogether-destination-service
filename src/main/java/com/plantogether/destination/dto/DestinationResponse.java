package com.plantogether.destination.dto;

import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationStatus;
import com.plantogether.destination.model.DestinationVote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
  // Legacy field — will be removed in Phase 3.
  private UUID proposedByDeviceId;
  private UUID proposedByMemberId;
  private Instant createdAt;
  private Instant updatedAt;
  private DestinationStatus status;
  private Instant chosenAt;
  // Legacy field — will be removed in Phase 3.
  private UUID chosenByDeviceId;
  private UUID chosenByMemberId;
  private VoteAggregate votes;

  public static DestinationResponse from(
      Destination entity, List<DestinationVote> votesForDestination, UUID viewerDeviceId) {
    Map<Integer, Integer> rankVotes = new HashMap<>();
    DestinationVote myVote = null;
    for (DestinationVote v : votesForDestination) {
      if (v.getRank() != null) {
        rankVotes.merge(v.getRank(), 1, Integer::sum);
      }
      if (viewerDeviceId != null && viewerDeviceId.equals(v.getDeviceId())) {
        myVote = v;
      }
    }
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
        .proposedByMemberId(entity.getProposedByTripMemberId())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .status(entity.getStatus() == null ? DestinationStatus.PROPOSED : entity.getStatus())
        .chosenAt(entity.getChosenAt())
        .chosenByDeviceId(entity.getChosenBy())
        .chosenByMemberId(entity.getChosenByTripMemberId())
        .votes(
            VoteAggregate.builder()
                .totalVotes(votesForDestination.size())
                .rankVotes(rankVotes)
                .myVoteCast(myVote != null)
                .myRank(myVote == null ? null : myVote.getRank())
                .build())
        .build();
  }

  public static DestinationResponse from(
      Destination entity, List<DestinationVote> votesForDestination) {
    return from(entity, votesForDestination, null);
  }

  public static DestinationResponse from(Destination entity) {
    return from(entity, List.of(), null);
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VoteAggregate {
    private int totalVotes;
    private Map<Integer, Integer> rankVotes;

    /** True iff the calling device has any vote row on this destination. */
    private boolean myVoteCast;

    /** The calling device's rank on this destination, or null if none / not in RANKING mode. */
    private Integer myRank;

    public static VoteAggregate empty() {
      return VoteAggregate.builder()
          .totalVotes(0)
          .rankVotes(Map.of())
          .myVoteCast(false)
          .myRank(null)
          .build();
    }
  }
}
