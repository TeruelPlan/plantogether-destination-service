package com.plantogether.destination.event.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plantogether.common.event.DestinationCommentAddedEvent;
import com.plantogether.destination.config.RabbitConfig;
import com.plantogether.destination.event.DestinationCommentAddedInternalEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies AFTER_COMMIT semantics of DestinationCommentBroadcaster: the RabbitMQ message is only
 * sent once the surrounding transaction commits; a rollback suppresses it.
 */
@SpringBootTest(
    classes = {DestinationCommentBroadcaster.class, DestinationCommentBroadcasterTest.Config.class})
class DestinationCommentBroadcasterTest {

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @MockitoBean private RabbitTemplate rabbitTemplate;

  @Test
  void onCommentAdded_afterCommit_sendsToExchangeWithCommentRoutingKey() {
    UUID tripId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    UUID authorMemberId = UUID.randomUUID();
    Instant ts = Instant.parse("2026-04-25T10:00:00Z");

    transactionTemplate.executeWithoutResult(
        status -> {
          eventPublisher.publishEvent(
              new DestinationCommentAddedInternalEvent(
                  tripId, destinationId, commentId, authorMemberId, ts));
          verify(rabbitTemplate, never())
              .convertAndSend(
                  eq(RabbitConfig.EXCHANGE),
                  eq(RabbitConfig.ROUTING_KEY_COMMENT_ADDED),
                  any(Object.class));
        });

    ArgumentCaptor<DestinationCommentAddedEvent> captor =
        ArgumentCaptor.forClass(DestinationCommentAddedEvent.class);
    verify(rabbitTemplate, times(1))
        .convertAndSend(
            eq(RabbitConfig.EXCHANGE),
            eq(RabbitConfig.ROUTING_KEY_COMMENT_ADDED),
            captor.capture());

    DestinationCommentAddedEvent sent = captor.getValue();
    assertThat(sent.getTripId()).isEqualTo(tripId);
    assertThat(sent.getDestinationId()).isEqualTo(destinationId);
    assertThat(sent.getCommentId()).isEqualTo(commentId);
    assertThat(sent.getAuthorMemberId()).isEqualTo(authorMemberId);
    assertThat(sent.getOccurredAt()).isEqualTo(ts);
  }

  @Test
  void onCommentAdded_rollback_sendsNothing() {
    transactionTemplate.executeWithoutResult(
        status -> {
          eventPublisher.publishEvent(
              new DestinationCommentAddedInternalEvent(
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  Instant.now()));
          status.setRollbackOnly();
        });

    verify(rabbitTemplate, never())
        .convertAndSend(
            eq(RabbitConfig.EXCHANGE),
            eq(RabbitConfig.ROUTING_KEY_COMMENT_ADDED),
            any(Object.class));
  }

  @TestConfiguration
  @org.springframework.transaction.annotation.EnableTransactionManagement
  static class Config {

    @Bean
    ConnectionFactory connectionFactory() {
      return mock(ConnectionFactory.class);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
          return new Object();
        }

        @Override
        protected void doBegin(
            Object transaction, org.springframework.transaction.TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
      };
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
      return new TransactionTemplate(tm);
    }
  }
}
