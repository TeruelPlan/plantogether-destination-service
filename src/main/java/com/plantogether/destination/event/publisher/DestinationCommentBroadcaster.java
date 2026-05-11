package com.plantogether.destination.event.publisher;

import com.plantogether.common.event.DestinationCommentAddedEvent;
import com.plantogether.destination.config.RabbitConfig;
import com.plantogether.destination.event.DestinationCommentAddedInternalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DestinationCommentBroadcaster {

  private final RabbitTemplate rabbitTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentAdded(DestinationCommentAddedInternalEvent e) {
    DestinationCommentAddedEvent msg =
        DestinationCommentAddedEvent.builder()
            .tripId(e.tripId())
            .destinationId(e.destinationId())
            .commentId(e.commentId())
            .authorMemberId(e.authorMemberId())
            .occurredAt(e.occurredAt())
            .build();
    rabbitTemplate.convertAndSend(
        RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY_COMMENT_ADDED, msg);
    log.debug(
        "Published destination.comment.added trip={} destination={} comment={}",
        e.tripId(),
        e.destinationId(),
        e.commentId());
  }
}
