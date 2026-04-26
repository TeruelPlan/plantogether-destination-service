package com.plantogether.destination.event.publisher;

import com.plantogether.common.event.DestinationChosenEvent;
import com.plantogether.destination.config.RabbitConfig;
import com.plantogether.destination.event.DestinationChosenInternalEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DestinationChosenBroadcaster {

  private final RabbitTemplate rabbitTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDestinationChosen(DestinationChosenInternalEvent internal) {
    DestinationChosenEvent event =
        DestinationChosenEvent.builder()
            .tripId(internal.tripId().toString())
            .destinationId(internal.destinationId().toString())
            .destinationName(internal.destinationName())
            .chosenByDeviceId(internal.chosenByDeviceId().toString())
            .chosenAt(internal.chosenAt())
            .previousChosenDestinationId(
                internal.previousChosenDestinationId() == null
                    ? null
                    : internal.previousChosenDestinationId().toString())
            .occurredAt(Instant.now())
            .build();
    rabbitTemplate.convertAndSend(
        RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY_DESTINATION_CHOSEN, event);
  }
}
