package com.example.settlement.infrastructure.acl;

import com.example.settlement.domain.paymentsummary.PaymentSummary;
import com.example.settlement.domain.paymentsummary.PaymentSummaryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentEventTranslator ACL 테스트")
class PaymentEventTranslatorTest {

    private PaymentEventTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new PaymentEventTranslator();
    }

    @Test
    @DisplayName("외부 APPROVED 이벤트를 PaymentSummary로 변환")
    void translateApprovedEvent() {
        // given
        ExternalPaymentEvent event = ExternalPaymentEvent.builder()
            .eventId("evt-001")
            .eventType("PAYMENT_APPROVED")
            .timestamp(LocalDateTime.of(2024, 1, 15, 10, 30, 0))
            .payload(ExternalPaymentEvent.Payload.builder()
                .paymentId(1L)
                .merchantId(1L)
                .walletId(1L)
                .orderId("ORDER-001")
                .amount(new BigDecimal("50000"))
                .status("APPROVED")
                .pgTransactionId("PG-TX-001")
                .build())
            .build();

        // when
        PaymentSummary summary = translator.toApprovedSummary(event);

        // then
        assertThat(summary.getPaymentId()).isEqualTo(1L);
        assertThat(summary.getMerchantId()).isEqualTo(1L);
        assertThat(summary.getOrderId()).isEqualTo("ORDER-001");
        assertThat(summary.getAmount().getAmount()).isEqualByComparingTo("50000");
        assertThat(summary.getStatus()).isEqualTo(PaymentSummaryStatus.APPROVED);
        assertThat(summary.getPaymentDate()).isEqualTo(event.getTimestamp().toLocalDate());
    }

    @Test
    @DisplayName("외부 CANCELLED 이벤트를 PaymentSummary로 변환 - 음수 ID")
    void translateCancelledEvent() {
        // given
        ExternalPaymentEvent event = ExternalPaymentEvent.builder()
            .eventId("evt-002")
            .eventType("PAYMENT_CANCELLED")
            .timestamp(LocalDateTime.of(2024, 1, 15, 14, 0, 0))
            .payload(ExternalPaymentEvent.Payload.builder()
                .paymentId(1L)
                .merchantId(1L)
                .walletId(1L)
                .orderId("ORDER-001")
                .amount(new BigDecimal("50000"))
                .status("CANCELLED")
                .build())
            .build();

        // when
        PaymentSummary summary = translator.toCancelledSummary(event);

        // then
        // 취소는 음수 ID로 구분
        assertThat(summary.getPaymentId()).isEqualTo(-1L);
        assertThat(summary.getStatus()).isEqualTo(PaymentSummaryStatus.CANCELLED);
    }

    @Test
    @DisplayName("Bounded Context 격리 - 외부 스키마 변경에 독립적")
    void boundedContextIsolation() {
        // ACL은 외부 이벤트 스키마가 변경되어도
        // 내부 도메인 모델(PaymentSummary)에 영향을 주지 않음

        ExternalPaymentEvent event = ExternalPaymentEvent.builder()
            .eventId("evt-003")
            .eventType("PAYMENT_APPROVED")
            .timestamp(LocalDateTime.now())
            .payload(ExternalPaymentEvent.Payload.builder()
                .paymentId(100L)
                .merchantId(1L)
                .walletId(1L)
                .orderId("ORDER-100")
                .amount(new BigDecimal("75000"))
                .status("APPROVED")
                // pgTransactionId가 없어도 동작
                .build())
            .build();

        PaymentSummary summary = translator.toApprovedSummary(event);

        // 내부 도메인 모델은 정상 생성됨
        assertThat(summary).isNotNull();
        assertThat(summary.isApproved()).isTrue();
    }
}
