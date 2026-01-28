package com.example.settlement.interfaces.messaging;

import com.example.settlement.domain.paymentsummary.PaymentSummary;
import com.example.settlement.domain.paymentsummary.PaymentSummaryRepository;
import com.example.settlement.infrastructure.acl.ExternalPaymentEvent;
import com.example.settlement.infrastructure.acl.PaymentEventTranslator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentSummaryRepository paymentSummaryRepository;
    private final PaymentEventTranslator translator;

    @KafkaListener(
        topics = "payment.approved",
        groupId = "settlement-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentApproved(ExternalPaymentEvent event) {
        log.info("Received payment.approved event: eventId={}, paymentId={}",
            event.getEventId(), event.getPayload().getPaymentId());

        try {
            if (paymentSummaryRepository.findByPaymentId(event.getPayload().getPaymentId()).isPresent()) {
                log.warn("Duplicate event received: paymentId={}", event.getPayload().getPaymentId());
                return;
            }

            PaymentSummary summary = translator.toApprovedSummary(event);
            paymentSummaryRepository.save(summary);

            log.info("Payment summary saved: paymentId={}, merchantId={}, amount={}",
                summary.getPaymentId(), summary.getMerchantId(), summary.getAmount());

        } catch (Exception e) {
            log.error("Failed to process payment.approved event: eventId={}", event.getEventId(), e);
            throw e;
        }
    }

    @KafkaListener(
        topics = "payment.cancelled",
        groupId = "settlement-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentCancelled(ExternalPaymentEvent event) {
        log.info("Received payment.cancelled event: eventId={}, paymentId={}",
            event.getEventId(), event.getPayload().getPaymentId());

        try {
            paymentSummaryRepository.findByPaymentId(event.getPayload().getPaymentId())
                .ifPresentOrElse(
                    existing -> {
                        PaymentSummary cancellation = translator.toCancelledSummary(event);
                        paymentSummaryRepository.save(cancellation);
                        log.info("Cancellation summary saved: paymentId={}",
                            event.getPayload().getPaymentId());
                    },
                    () -> log.warn("Original payment not found for cancellation: paymentId={}",
                        event.getPayload().getPaymentId())
                );

        } catch (Exception e) {
            log.error("Failed to process payment.cancelled event: eventId={}", event.getEventId(), e);
            throw e;
        }
    }
}
