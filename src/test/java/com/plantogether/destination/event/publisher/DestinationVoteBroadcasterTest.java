package com.plantogether.destination.event.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plantogether.common.event.VoteCastEvent;
import com.plantogether.destination.config.RabbitConfig;
import com.plantogether.destination.event.VoteCastInternalEvent;
import com.plantogether.destination.model.VoteMode;
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
 * Verifies AFTER_COMMIT semantics of DestinationVoteBroadcaster: the RabbitMQ message is only sent
 * once the surrounding transaction commits; a rollback suppresses it.
 */
@SpringBootTest(
    classes = {DestinationVoteBroadcaster.class, DestinationVoteBroadcasterTest.Config.class})
class DestinationVoteBroadcasterTest {

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private RabbitTemplate rabbitTemplate;

  @Test
  void publishesVoteCastEvent_onCommit() {
    UUID tripId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    transactionTemplate.executeWithoutResult(
        status -> {
          eventPublisher.publishEvent(
              new VoteCastInternalEvent(tripId, destinationId, memberId, VoteMode.SIMPLE, "YES"));
          // Before commit: no message sent yet
          verify(rabbitTemplate, never())
              .convertAndSend(
                  eq(RabbitConfig.EXCHANGE),
                  eq(RabbitConfig.ROUTING_KEY_VOTE_CAST),
                  any(Object.class));
        });

    ArgumentCaptor<VoteCastEvent> captor = ArgumentCaptor.forClass(VoteCastEvent.class);
    verify(rabbitTemplate, times(1))
        .convertAndSend(
            eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.ROUTING_KEY_VOTE_CAST), captor.capture());

    VoteCastEvent sent = captor.getValue();
    assertThat(sent.getTripId()).isEqualTo(tripId.toString());
    assertThat(sent.getDestinationId()).isEqualTo(destinationId.toString());
    assertThat(sent.getTripMemberId()).isEqualTo(memberId.toString());
    assertThat(sent.getVoteMode()).isEqualTo("SIMPLE");
    assertThat(sent.getVoteValue()).isEqualTo("YES");
    assertThat(sent.getOccurredAt()).isNotNull();
  }

  @Test
  void noMessageSent_whenTransactionRollsBack() {
    UUID tripId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    transactionTemplate.executeWithoutResult(
        status -> {
          eventPublisher.publishEvent(
              new VoteCastInternalEvent(tripId, destinationId, memberId, VoteMode.RANKING, "1"));
          status.setRollbackOnly();
        });

    verify(rabbitTemplate, never())
        .convertAndSend(
            eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.ROUTING_KEY_VOTE_CAST), any(Object.class));
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
      // Minimal in-memory transaction manager — no real datasource, just drives
      // the Spring transaction synchronization lifecycle so AFTER_COMMIT listeners fire.
      return new AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
          return new Object();
        }

        @Override
        protected void doBegin(
            Object transaction, org.springframework.transaction.TransactionDefinition definition) {
          // no-op
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
          // no-op — synchronization layer triggers AFTER_COMMIT callbacks
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
          // no-op
        }
      };
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
      return new TransactionTemplate(tm);
    }
  }
}
