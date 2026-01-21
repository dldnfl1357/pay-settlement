package com.example.settlement.event;

import com.example.settlement.domain.PaymentSummary;
import com.example.settlement.repository.PaymentSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentSummaryRepository paymentSummaryRepository;

    @KafkaListener(
        topics = "payment.approved",
        groupId = "settlement-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentApproved(PaymentEvent event) {
        log.info("Received payment.approved event: eventId={}, paymentId={}",
            event.getEventId(), event.getPayload().getPaymentId());

        try {
            // 중복 체크
            if (paymentSummaryRepository.findByPaymentId(event.getPayload().getPaymentId()).isPresent()) {
                log.warn("Duplicate event received: paymentId={}", event.getPayload().getPaymentId());
                return;
            }

            PaymentSummary summary = PaymentSummary.builder()
                .paymentId(event.getPayload().getPaymentId())
                .merchantId(event.getPayload().getMerchantId())
                .walletId(event.getPayload().getWalletId())
                .orderId(event.getPayload().getOrderId())
                .amount(event.getPayload().getAmount())
                .status("APPROVED")
                .paymentDate(event.getTimestamp().toLocalDate())
                .settled(false)
                .build();

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
    public void handlePaymentCancelled(PaymentEvent event) {
        log.info("Received payment.cancelled event: eventId={}, paymentId={}",
            event.getEventId(), event.getPayload().getPaymentId());

        try {
            // 기존 APPROVED 레코드 찾기
            paymentSummaryRepository.findByPaymentId(event.getPayload().getPaymentId())
                .ifPresentOrElse(
                    existing -> {
                        // 이미 존재하면 CANCELLED로 상태 변경하는 새 레코드 추가
                        PaymentSummary cancellation = PaymentSummary.builder()
                            .paymentId(-event.getPayload().getPaymentId())  // 음수로 구분
                            .merchantId(event.getPayload().getMerchantId())
                            .walletId(event.getPayload().getWalletId())
                            .orderId(event.getPayload().getOrderId())
                            .amount(event.getPayload().getAmount())
                            .status("CANCELLED")
                            .paymentDate(event.getTimestamp().toLocalDate())
                            .settled(false)
                            .build();
                        paymentSummaryRepository.save(cancellation);
                        log.info("Cancellation summary saved: paymentId={}", event.getPayload().getPaymentId());
                    },
                    () -> {
                        // 원본이 없으면 무시
                        log.warn("Original payment not found for cancellation: paymentId={}",
                            event.getPayload().getPaymentId());
                    }
                );

        } catch (Exception e) {
            log.error("Failed to process payment.cancelled event: eventId={}", event.getEventId(), e);
            throw e;
        }
    }
}
