package com.plantogether.destination.repository;

import com.plantogether.destination.model.DestinationVote;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationVoteRepository extends JpaRepository<DestinationVote, UUID> {

  Optional<DestinationVote> findByDestinationIdAndDeviceId(UUID destinationId, UUID deviceId);

  List<DestinationVote> findByTripIdAndDeviceId(UUID tripId, UUID deviceId);

  List<DestinationVote> findByDestinationIdIn(Collection<UUID> destinationIds);

  Optional<DestinationVote> findByTripIdAndDeviceIdAndRank(
      UUID tripId, UUID deviceId, Integer rank);

  @Modifying
  @Query(
      "delete from DestinationVote v where v.destinationId = :destinationId and v.deviceId ="
          + " :deviceId")
  int deleteByDestinationIdAndDeviceId(UUID destinationId, UUID deviceId);

  @Modifying
  @Query(
      "delete from DestinationVote v where v.tripId = :tripId and v.deviceId = :deviceId and"
          + " v.destinationId <> :keepDestinationId")
  int deleteOtherTripVotes(UUID tripId, UUID deviceId, UUID keepDestinationId);

  @Modifying
  @Query(
      "update DestinationVote v set v.rank = null, v.updatedAt = current_timestamp where v.tripId ="
          + " :tripId and v.deviceId = :deviceId and v.rank = :rank and v.destinationId <>"
          + " :keepDestinationId")
  int clearRankForSwap(UUID tripId, UUID deviceId, Integer rank, UUID keepDestinationId);

  @Modifying
  @Query(
      "update DestinationVote v set v.rank = null, v.updatedAt = current_timestamp where v.tripId ="
          + " :tripId")
  int nullRanksForTrip(UUID tripId);
}
