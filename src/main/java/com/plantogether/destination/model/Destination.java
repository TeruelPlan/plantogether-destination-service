package com.plantogether.destination.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "destination")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Destination {

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "trip_id", nullable = false)
  private UUID tripId;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "image_key", length = 500)
  private String imageKey;

  @Column(name = "estimated_budget", precision = 19, scale = 4)
  private BigDecimal estimatedBudget;

  @Column(length = 3)
  private String currency;

  @Column(name = "external_url", length = 512)
  private String externalUrl;

  @Column(name = "proposed_by", nullable = false)
  private UUID proposedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DestinationStatus status;

  @Column(name = "chosen_at")
  private Instant chosenAt;

  @Column(name = "chosen_by")
  private UUID chosenBy;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (status == null) status = DestinationStatus.PROPOSED;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
