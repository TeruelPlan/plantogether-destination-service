package com.plantogether.destination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCommentRequest {

  @NotBlank(message = "Comment cannot be empty")
  @Size(min = 1, max = 2000, message = "Comment must be at most 2000 characters")
  private String content;
}
