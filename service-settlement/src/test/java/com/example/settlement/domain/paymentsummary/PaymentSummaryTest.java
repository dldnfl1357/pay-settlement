package com.example.settlement.domain.paymentsummary;

import com.example.settlement.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentSummary 테스트")
class PaymentSummaryTest {

    @Test
    @DisplayName("APPROVED 상태 생성")
    void createApprovedSummary() {
        PaymentSummary summary = PaymentSummary.create(
            1L,  // paymentId
            1L,  // merchantId
            1L,  // walletId
            "ORDER-001",
            Money.of(50000),
            PaymentSummaryStatus.APPROVED,
            LocalDate.of(2024, 1, 15)
        );

        assertThat(summary.getPaymentId()).isEqualTo(1L);
        assertThat(summary.getStatus()).isEqualTo(PaymentSummaryStatus.APPROVED);
        assertThat(summary.isApproved()).isTrue();
        assertThat(summary.isCancelled()).isFalse();
        assertThat(summary.getSettled()).isFalse();
    }

    @Test
    @DisplayName("CANCELLED 상태 생성")
    void createCancelledSummary() {
        PaymentSummary summary = PaymentSummary.create(
            -1L,  // 음수로 취소 구분
            1L,
            1L,
            "ORDER-001",
            Money.of(50000),
            PaymentSummaryStatus.CANCELLED,
            LocalDate.of(2024, 1, 15)
        );

        assertThat(summary.getStatus()).isEqualTo(PaymentSummaryStatus.CANCELLED);
        assertThat(summary.isApproved()).isFalse();
        assertThat(summary.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("정산 완료 처리")
    void markAsSettled() {
        PaymentSummary summary = PaymentSummary.create(
            1L, 1L, 1L, "ORDER-001",
            Money.of(50000),
            PaymentSummaryStatus.APPROVED,
            LocalDate.now()
        );

        assertThat(summary.getSettled()).isFalse();

        summary.markAsSettled();

        assertThat(summary.getSettled()).isTrue();
    }
}
