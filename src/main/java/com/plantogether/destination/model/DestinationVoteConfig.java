package com.plantogether.destination.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "destination_vote_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DestinationVoteConfig {

    @Id
    @Column(name = "trip_id", updatable = false, nullable = false)
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VoteMode mode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        if (updatedAt == null) updatedAt = Instant.now();
    }
}
