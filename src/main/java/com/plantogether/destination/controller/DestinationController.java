package com.plantogether.destination.controller;

import com.plantogether.destination.dto.DestinationResponse;
import com.plantogether.destination.dto.ProposeDestinationRequest;
import com.plantogether.destination.service.DestinationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService destinationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DestinationResponse propose(Authentication authentication,
                                       @PathVariable UUID tripId,
                                       @Valid @RequestBody ProposeDestinationRequest request) {
        return destinationService.proposeDestination(tripId, authentication.getName(), request);
    }

    @GetMapping
    public List<DestinationResponse> list(Authentication authentication,
                                          @PathVariable UUID tripId) {
        return destinationService.listDestinations(tripId, authentication.getName());
    }
}
