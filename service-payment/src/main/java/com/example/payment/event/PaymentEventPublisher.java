package com.example.payment.event;

import com.example.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    private static final String TOPIC_REQUESTED = "payment.requested";
    private static final String TOPIC_APPROVED = "payment.approved";
    private static final String TOPIC_CANCELLED = "payment.cancelled";
    private static final String TOPIC_FAILED = "payment.failed";

    public void publishRequested(Payment payment) {
        PaymentEvent event = PaymentEvent.fromPayment(payment, PaymentEvent.EventType.PAYMENT_REQUESTED);
        send(TOPIC_REQUESTED, payment.getId().toString(), event);
    }

    public void publishApproved(Payment payment) {
        PaymentEvent event = PaymentEvent.fromPayment(payment, PaymentEvent.EventType.PAYMENT_APPROVED);
        send(TOPIC_APPROVED, payment.getId().toString(), event);
    }

    public void publishCancelled(Payment payment) {
        PaymentEvent event = PaymentEvent.fromPayment(payment, PaymentEvent.EventType.PAYMENT_CANCELLED);
        send(TOPIC_CANCELLED, payment.getId().toString(), event);
    }

    public void publishFailed(Payment payment) {
        PaymentEvent event = PaymentEvent.fromPayment(payment, PaymentEvent.EventType.PAYMENT_FAILED);
        send(TOPIC_FAILED, payment.getId().toString(), event);
    }

    private void send(String topic, String key, PaymentEvent event) {
        kafkaTemplate.send(topic, key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event: topic={}, key={}, eventId={}",
                        topic, key, event.getEventId(), ex);
                } else {
                    log.info("Event published: topic={}, key={}, eventId={}, offset={}",
                        topic, key, event.getEventId(),
                        result.getRecordMetadata().offset());
                }
            });
    }
}
