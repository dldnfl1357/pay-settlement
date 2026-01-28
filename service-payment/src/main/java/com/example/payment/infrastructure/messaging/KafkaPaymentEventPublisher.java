package com.example.payment.infrastructure.messaging;

import com.example.payment.application.port.PaymentEventPublisher;
import com.example.payment.domain.payment.event.PaymentApprovedEvent;
import com.example.payment.domain.payment.event.PaymentCancelledEvent;
import com.example.payment.domain.payment.event.PaymentCreatedEvent;
import com.example.payment.domain.payment.event.PaymentFailedEvent;
import com.example.payment.domain.shared.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_REQUESTED = "payment.requested";
    private static final String TOPIC_APPROVED = "payment.approved";
    private static final String TOPIC_CANCELLED = "payment.cancelled";
    private static final String TOPIC_FAILED = "payment.failed";

    @Override
    public void publish(DomainEvent event) {
        String topic = resolveTopic(event);
        String key = resolveKey(event);
        Map<String, Object> payload = toPayload(event);

        kafkaTemplate.send(topic, key, payload)
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

    private String resolveTopic(DomainEvent event) {
        if (event instanceof PaymentCreatedEvent) return TOPIC_REQUESTED;
        if (event instanceof PaymentApprovedEvent) return TOPIC_APPROVED;
        if (event instanceof PaymentCancelledEvent) return TOPIC_CANCELLED;
        if (event instanceof PaymentFailedEvent) return TOPIC_FAILED;
        throw new IllegalArgumentException("Unknown event type: " + event.getClass().getSimpleName());
    }

    private String resolveKey(DomainEvent event) {
        if (event instanceof PaymentCreatedEvent e) return str(e.getPaymentId());
        if (event instanceof PaymentApprovedEvent e) return str(e.getPaymentId());
        if (event instanceof PaymentCancelledEvent e) return str(e.getPaymentId());
        if (event instanceof PaymentFailedEvent e) return str(e.getPaymentId());
        return event.getEventId();
    }

    private Map<String, Object> toPayload(DomainEvent event) {
        if (event instanceof PaymentCreatedEvent e) {
            return Map.of(
                "eventId", e.getEventId(),
                "eventType", "PAYMENT_REQUESTED",
                "timestamp", e.getOccurredAt().toString(),
                "payload", Map.of(
                    "paymentId", nullSafe(e.getPaymentId()),
                    "merchantId", e.getMerchantId(),
                    "walletId", e.getWalletId(),
                    "orderId", e.getOrderId(),
                    "amount", e.getAmount(),
                    "status", "PENDING"
                )
            );
        }
        if (event instanceof PaymentApprovedEvent e) {
            return Map.of(
                "eventId", e.getEventId(),
                "eventType", "PAYMENT_APPROVED",
                "timestamp", e.getOccurredAt().toString(),
                "payload", Map.of(
                    "paymentId", e.getPaymentId(),
                    "merchantId", e.getMerchantId(),
                    "walletId", e.getWalletId(),
                    "orderId", e.getOrderId(),
                    "amount", e.getAmount(),
                    "status", "APPROVED",
                    "pgTransactionId", e.getPgTransactionId()
                )
            );
        }
        if (event instanceof PaymentCancelledEvent e) {
            return Map.of(
                "eventId", e.getEventId(),
                "eventType", "PAYMENT_CANCELLED",
                "timestamp", e.getOccurredAt().toString(),
                "payload", Map.of(
                    "paymentId", e.getPaymentId(),
                    "merchantId", e.getMerchantId(),
                    "walletId", e.getWalletId(),
                    "orderId", e.getOrderId(),
                    "amount", e.getAmount(),
                    "status", "CANCELLED"
                )
            );
        }
        if (event instanceof PaymentFailedEvent e) {
            return Map.of(
                "eventId", e.getEventId(),
                "eventType", "PAYMENT_FAILED",
                "timestamp", e.getOccurredAt().toString(),
                "payload", Map.of(
                    "paymentId", nullSafe(e.getPaymentId()),
                    "merchantId", e.getMerchantId(),
                    "walletId", e.getWalletId(),
                    "orderId", e.getOrderId(),
                    "amount", e.getAmount(),
                    "status", "FAILED"
                )
            );
        }
        throw new IllegalArgumentException("Unknown event type: " + event.getClass().getSimpleName());
    }

    private String str(Object obj) {
        return obj != null ? obj.toString() : "unknown";
    }

    private Object nullSafe(Object obj) {
        return obj != null ? obj : "unknown";
    }
}
