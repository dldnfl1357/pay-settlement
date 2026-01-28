package com.example.payment.domain.payment;

import com.example.payment.domain.payment.event.PaymentApprovedEvent;
import com.example.payment.domain.payment.event.PaymentCancelledEvent;
import com.example.payment.domain.payment.event.PaymentCreatedEvent;
import com.example.payment.domain.payment.event.PaymentFailedEvent;
import com.example.payment.domain.payment.exception.InvalidPaymentStateException;
import com.example.payment.domain.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Payment Aggregate 테스트")
class PaymentTest {

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = Payment.create(
            1L,  // walletId
            1L,  // merchantId
            OrderId.of("ORDER-001"),
            Money.of(50000),
            CardToken.of("tok_test_1111"),
            IdempotencyKey.of("idem-key-001"),
            "테스트 상품"
        );
    }

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("결제 생성 시 PENDING 상태")
        void initialStatusIsPending() {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("결제 생성 시 PaymentCreatedEvent 발행")
        void createdEventIsRegistered() {
            assertThat(payment.getDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(PaymentCreatedEvent.class);
        }

        @Test
        @DisplayName("금액이 0 이하면 예외")
        void zeroAmountThrowsException() {
            assertThatThrownBy(() -> Payment.create(
                1L, 1L,
                OrderId.of("ORDER-001"),
                Money.of(0),
                CardToken.of("tok_test_1111"),
                IdempotencyKey.of("idem-key-002"),
                "테스트"
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("만료 시간 설정됨")
        void expirationTimeIsSet() {
            assertThat(payment.getExpiredAt()).isNotNull();
            assertThat(payment.getExpiredAt()).isAfter(payment.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("승인")
    class Approval {

        @Test
        @DisplayName("PENDING 상태에서 승인 성공")
        void approveFromPending() {
            payment.clearDomainEvents(); // 생성 이벤트 클리어

            payment.approve("PG-TX-001");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getPgTransactionId()).isEqualTo("PG-TX-001");
            assertThat(payment.getApprovedAt()).isNotNull();
        }

        @Test
        @DisplayName("승인 시 PaymentApprovedEvent 발행")
        void approvedEventIsRegistered() {
            payment.clearDomainEvents();

            payment.approve("PG-TX-001");

            assertThat(payment.getDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(PaymentApprovedEvent.class);
        }

        @Test
        @DisplayName("APPROVED 상태에서 재승인 시 예외")
        void cannotApproveAlreadyApproved() {
            payment.approve("PG-TX-001");

            assertThatThrownBy(() -> payment.approve("PG-TX-002"))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("APPROVED");
        }

        @Test
        @DisplayName("FAILED 상태에서 승인 시 예외")
        void cannotApproveFailedPayment() {
            payment.fail("테스트 실패");

            assertThatThrownBy(() -> payment.approve("PG-TX-001"))
                .isInstanceOf(InvalidPaymentStateException.class);
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancellation {

        @Test
        @DisplayName("APPROVED 상태에서 취소 성공")
        void cancelFromApproved() {
            payment.approve("PG-TX-001");
            payment.clearDomainEvents();

            payment.cancel();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(payment.getCancelledAt()).isNotNull();
        }

        @Test
        @DisplayName("취소 시 PaymentCancelledEvent 발행")
        void cancelledEventIsRegistered() {
            payment.approve("PG-TX-001");
            payment.clearDomainEvents();

            payment.cancel();

            assertThat(payment.getDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(PaymentCancelledEvent.class);
        }

        @Test
        @DisplayName("PENDING 상태에서 취소 시 예외")
        void cannotCancelPendingPayment() {
            assertThatThrownBy(() -> payment.cancel())
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("APPROVED");
        }
    }

    @Nested
    @DisplayName("실패")
    class Failure {

        @Test
        @DisplayName("PENDING 상태에서 실패 처리")
        void failFromPending() {
            payment.clearDomainEvents();

            payment.fail("PG 승인 실패");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("실패 시 PaymentFailedEvent 발행")
        void failedEventIsRegistered() {
            payment.clearDomainEvents();

            payment.fail("PG 승인 실패");

            assertThat(payment.getDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(PaymentFailedEvent.class);

            PaymentFailedEvent event = (PaymentFailedEvent) payment.getDomainEvents().get(0);
            assertThat(event.getReason()).isEqualTo("PG 승인 실패");
        }

        @Test
        @DisplayName("APPROVED 상태에서 실패 처리 시 예외")
        void cannotFailApprovedPayment() {
            payment.approve("PG-TX-001");

            assertThatThrownBy(() -> payment.fail("테스트"))
                .isInstanceOf(InvalidPaymentStateException.class);
        }
    }

    @Nested
    @DisplayName("상태 전이 다이어그램")
    class StateTransitions {

        @Test
        @DisplayName("PENDING → APPROVED → CANCELLED")
        void normalCancelFlow() {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

            payment.approve("PG-TX-001");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);

            payment.cancel();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("PENDING → FAILED (PG 실패)")
        void pgFailureFlow() {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

            payment.fail("PG 잔액 부족");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }
}
