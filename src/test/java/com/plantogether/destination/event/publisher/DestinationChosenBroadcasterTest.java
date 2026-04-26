package com.plantogether.destination.event.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plantogether.common.event.DestinationChosenEvent;
import com.plantogether.destination.config.RabbitConfig;
import com.plantogether.destination.event.DestinationChosenInternalEvent;
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

@SpringBootTest(
    classes = {DestinationChosenBroadcaster.class, DestinationChosenBroadcasterTest.Config.class})
class DestinationChosenBroadcasterTest {

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @MockitoBean private RabbitTemplate rabbitTemplate;

  @Test
  void afterCommit_sendsToExchangeWithRoutingKey() {
    UUID tripId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();
    Instant chosenAt = Instant.now();

    transactionTemplate.executeWithoutResult(
        status ->
            eventPublisher.publishEvent(
                new DestinationChosenInternalEvent(
                    tripId, destinationId, "Lisbon", deviceId, chosenAt, null)));

    ArgumentCaptor<DestinationChosenEvent> captor =
        ArgumentCaptor.forClass(DestinationChosenEvent.class);
    verify(rabbitTemplate, times(1))
        .convertAndSend(
            eq(RabbitConfig.EXCHANGE),
            eq(RabbitConfig.ROUTING_KEY_DESTINATION_CHOSEN),
            captor.capture());

    DestinationChosenEvent sent = captor.getValue();
    assertThat(sent.getTripId()).isEqualTo(tripId.toString());
    assertThat(sent.getDestinationId()).isEqualTo(destinationId.toString());
    assertThat(sent.getDestinationName()).isEqualTo("Lisbon");
    assertThat(sent.getChosenByDeviceId()).isEqualTo(deviceId.toString());
    assertThat(sent.getChosenAt()).isEqualTo(chosenAt);
    assertThat(sent.getPreviousChosenDestinationId()).isNull();
    assertThat(sent.getOccurredAt()).isNotNull();
  }

  @Test
  void rollback_doesNotSend() {
    UUID tripId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();

    transactionTemplate.executeWithoutResult(
        status -> {
          eventPublisher.publishEvent(
              new DestinationChosenInternalEvent(
                  tripId, destinationId, "Lisbon", deviceId, Instant.now(), null));
          status.setRollbackOnly();
        });

    verify(rabbitTemplate, never())
        .convertAndSend(
            eq(RabbitConfig.EXCHANGE),
            eq(RabbitConfig.ROUTING_KEY_DESTINATION_CHOSEN),
            any(Object.class));
  }

  @Test
  void event_carriesPreviousChosenDestinationId_whenPresent() {
    UUID previousId = UUID.randomUUID();

    transactionTemplate.executeWithoutResult(
        status ->
            eventPublisher.publishEvent(
                new DestinationChosenInternalEvent(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Lisbon",
                    UUID.randomUUID(),
                    Instant.now(),
                    previousId)));

    ArgumentCaptor<DestinationChosenEvent> captor =
        ArgumentCaptor.forClass(DestinationChosenEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(RabbitConfig.EXCHANGE),
            eq(RabbitConfig.ROUTING_KEY_DESTINATION_CHOSEN),
            captor.capture());
    assertThat(captor.getValue().getPreviousChosenDestinationId()).isEqualTo(previousId.toString());
  }

  @Test
  void event_carriesPreviousChosenDestinationId_null_whenAbsent() {
    transactionTemplate.executeWithoutResult(
        status ->
            eventPublisher.publishEvent(
                new DestinationChosenInternalEvent(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Lisbon",
                    UUID.randomUUID(),
                    Instant.now(),
                    null)));

    ArgumentCaptor<DestinationChosenEvent> captor =
        ArgumentCaptor.forClass(DestinationChosenEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(RabbitConfig.EXCHANGE),
            eq(RabbitConfig.ROUTING_KEY_DESTINATION_CHOSEN),
            captor.capture());
    assertThat(captor.getValue().getPreviousChosenDestinationId()).isNull();
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
