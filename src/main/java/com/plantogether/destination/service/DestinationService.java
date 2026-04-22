package com.plantogether.destination.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.dto.ProposeDestinationRequest;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DestinationService {

    private final DestinationRepository repository;
    private final TripGrpcClient tripGrpcClient;

    @Transactional
    public DestinationResponse proposeDestination(UUID tripId, String deviceId, ProposeDestinationRequest req) {
        requireMember(tripId, deviceId);

        Destination entity = Destination.builder()
                .tripId(tripId)
                .name(req.getName())
                .description(req.getDescription())
                .imageKey(req.getImageKey())
                .estimatedBudget(req.getEstimatedBudget())
                .currency(req.getCurrency())
                .externalUrl(req.getExternalUrl())
                .proposedBy(UUID.fromString(deviceId))
                .build();

        Destination saved = repository.save(entity);
        return DestinationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DestinationResponse> listDestinations(UUID tripId, String deviceId) {
        requireMember(tripId, deviceId);
        return repository.findByTripIdOrderByCreatedAtDesc(tripId).stream()
                .map(DestinationResponse::from)
                .toList();
    }

    private void requireMember(UUID tripId, String deviceId) {
        if (!tripGrpcClient.isMember(tripId.toString(), deviceId)) {
            throw new AccessDeniedException("Device is not a member of this trip");
        }
    }
}
