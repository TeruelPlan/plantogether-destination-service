package com.plantogether.destination.event.publisher;

import com.plantogether.common.event.VoteCastEvent;
import com.plantogether.destination.config.RabbitConfig;
import com.plantogether.destination.event.VoteCastInternalEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DestinationVoteBroadcaster {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoteCast(VoteCastInternalEvent internal) {
        VoteCastEvent event = VoteCastEvent.builder()
                .tripId(internal.tripId().toString())
                .destinationId(internal.destinationId().toString())
                .deviceId(internal.deviceId().toString())
                .voteMode(internal.mode().name())
                .voteValue(internal.voteValue())
                .occurredAt(internal.occurredAt())
                .build();
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY_VOTE_CAST, event);
    }
}
