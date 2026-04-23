package com.plantogether.destination.dto;

import com.plantogether.destination.model.VoteMode;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteConfigRequest {

    @NotNull
    private VoteMode mode;
}
