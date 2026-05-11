package com.plantogether.destination.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationComment;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * AC9 — when several comments share the same created_at, listing must remain deterministic by
 * (created_at ASC, id ASC). Repository-level integration test against a real PostgreSQL via
 * Testcontainers (Flyway runs the production migrations).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Tag("integration")
class DestinationCommentRepositoryIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("plantogether_destination")
          .withUsername("plantogether")
          .withPassword("plantogether");

  @DynamicPropertySource
  static void overrideDb(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Autowired private DestinationCommentRepository commentRepository;
  @Autowired private EntityManager em;

  @Test
  void findByDestination_orderedByCreatedAtAscThenIdAsc_whenTimestampsCollide() {
    UUID tripId = UUID.randomUUID();
    Destination destination =
        Destination.builder()
            .tripId(tripId)
            .name("Lisbon")
            .proposedByTripMemberId(UUID.randomUUID())
            .build();
    em.persist(destination);
    em.flush();

    Instant sharedAt = Instant.parse("2026-04-25T10:00:00Z");
    DestinationComment c1 =
        DestinationComment.builder()
            .destinationId(destination.getId())
            .tripId(tripId)
            .tripMemberId(UUID.randomUUID())
            .content("first")
            .createdAt(sharedAt)
            .build();
    DestinationComment c2 =
        DestinationComment.builder()
            .destinationId(destination.getId())
            .tripId(tripId)
            .tripMemberId(UUID.randomUUID())
            .content("second")
            .createdAt(sharedAt)
            .build();
    DestinationComment c3 =
        DestinationComment.builder()
            .destinationId(destination.getId())
            .tripId(tripId)
            .tripMemberId(UUID.randomUUID())
            .content("third")
            .createdAt(sharedAt.plusSeconds(1))
            .build();
    em.persist(c1);
    em.persist(c2);
    em.persist(c3);
    em.flush();
    em.clear();

    List<DestinationComment> result =
        commentRepository.findByDestinationIdOrderByCreatedAtAscIdAsc(destination.getId());

    // c3 last (later created_at). For c1/c2 (same created_at) order is by id ASC.
    assertThat(result).hasSize(3);
    assertThat(result.get(2).getId()).isEqualTo(c3.getId());

    UUID firstId = result.get(0).getId();
    UUID secondId = result.get(1).getId();
    assertThat(firstId.compareTo(secondId)).isLessThan(0);
    assertThat(List.of(firstId, secondId)).containsExactlyInAnyOrder(c1.getId(), c2.getId());
  }
}
