package com.plantogether.destination.controller;

import com.plantogether.destination.dto.AddCommentRequest;
import com.plantogether.destination.dto.CommentResponse;
import com.plantogether.destination.service.DestinationCommentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

@RestController
@RequestMapping("/api/v1/destinations/{destinationId}/comments")
@RequiredArgsConstructor
public class DestinationCommentController {

  private final DestinationCommentService commentService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CommentResponse addComment(
      Authentication auth,
      @PathVariable UUID destinationId,
      @Valid @RequestBody AddCommentRequest req) {
    return commentService.addComment(destinationId, auth.getName(), req);
  }

  @GetMapping
  public List<CommentResponse> listComments(Authentication auth, @PathVariable UUID destinationId) {
    return commentService.listComments(destinationId, auth.getName());
  }
}
