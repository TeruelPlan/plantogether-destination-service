package com.plantogether.destination.controller;

import com.plantogether.destination.dto.CastVoteRequest;
import com.plantogether.destination.dto.VoteResponse;
import com.plantogether.destination.service.DestinationVoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/destinations/{destinationId}")
@RequiredArgsConstructor
public class DestinationVoteController {

    private final DestinationVoteService voteService;

    @PostMapping("/vote")
    @ResponseStatus(HttpStatus.OK)
    public VoteResponse castVote(Authentication authentication,
                                 @PathVariable UUID destinationId,
                                 @Valid @RequestBody(required = false) CastVoteRequest req) {
        CastVoteRequest effective = req != null ? req : CastVoteRequest.builder().build();
        return voteService.castVote(destinationId, authentication.getName(), effective);
    }

    @DeleteMapping("/vote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retractVote(Authentication authentication,
                            @PathVariable UUID destinationId) {
        voteService.retractVote(destinationId, authentication.getName());
    }
}
