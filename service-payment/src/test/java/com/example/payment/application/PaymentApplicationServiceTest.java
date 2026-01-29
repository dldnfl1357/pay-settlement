package com.example.payment.application;

import com.example.payment.application.dto.CreatePaymentCommand;
import com.example.payment.application.dto.PaymentResult;
import com.example.payment.application.exception.PaymentNotFoundException;
import com.example.payment.application.port.DistributedLockManager;
import com.example.payment.application.port.IdempotencyStore;
import com.example.payment.application.port.PaymentEventPublisher;
import com.example.payment.application.port.PgGateway;
import com.example.payment.domain.ledger.LedgerDomainService;
import com.example.payment.domain.ledger.LedgerEntry;
import com.example.payment.domain.ledger.LedgerEntryRepository;
import com.example.payment.domain.payment.*;
import com.example.payment.domain.payment.exception.DuplicatePaymentException;
import com.example.payment.domain.shared.Money;
import com.example.payment.domain.wallet.Wallet;
import com.example.payment.domain.wallet.WalletRepository;
import com.example.payment.infrastructure.metrics.PaymentMetrics;
import com.example.payment.infrastructure.pg.PgApprovalException;
import com.example.payment.infrastructure.pg.PgResponse;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentApplicationService 테스트")
class PaymentApplicationServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private PgGateway pgGateway;
    @Mock private IdempotencyStore idempotencyStore;
    @Mock private DistributedLockManager lockManager;
    @Mock private PaymentEventPublisher eventPublisher;
    @Mock private LedgerDomainService ledgerDomainService;
    @Mock private PaymentMetrics paymentMetrics;
    @Mock private Timer.Sample timerSample;

    @InjectMocks
    private PaymentApplicationService paymentService;

    private CreatePaymentCommand createCommand;
    private Payment payment;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        lenient().when(paymentMetrics.startTimer()).thenReturn(timerSample);

        createCommand = CreatePaymentCommand.builder()
            .walletId(1L)
            .merchantId(1L)
            .orderId("ORDER-001")
            .amount(new BigDecimal("50000"))
            .cardToken("tok_test_1111")
            .productName("테스트 상품")
            .idempotencyKey("idem-key-001")
            .build();

        payment = Payment.create(
            1L, 1L,
            OrderId.of("ORDER-001"),
            Money.of(50000),
            CardToken.of("tok_test_1111"),
            IdempotencyKey.of("idem-key-001"),
            "테스트 상품"
        );

        wallet = Wallet.create(1L, "테스트유저", Money.of(100000));
    }

    @Nested
    @DisplayName("결제 생성")
    class CreatePayment {

        @Test
        @DisplayName("정상 생성")
        void createPaymentSuccess() {
            // given
            when(idempotencyStore.getResponse(anyString(), any())).thenReturn(Optional.empty());
            when(idempotencyStore.tryAcquire(anyString())).thenReturn(true);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                // ID 설정을 위한 reflection 또는 그냥 반환
                return p;
            });

            // when
            PaymentResult result = paymentService.createPayment(createCommand);

            // then
            assertThat(result.getOrderId()).isEqualTo("ORDER-001");
            assertThat(result.getAmount()).isEqualByComparingTo("50000");
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);

            verify(idempotencyStore).complete(eq("idem-key-001"), any(PaymentResult.class));
            verify(eventPublisher, atLeastOnce()).publish(any());
        }

        @Test
        @DisplayName("멱등성 키 중복 - 캐시된 응답 반환")
        void idempotencyHit() {
            // given
            PaymentResult cachedResult = PaymentResult.builder()
                .id(1L)
                .orderId("ORDER-001")
                .status(PaymentStatus.PENDING)
                .build();
            when(idempotencyStore.getResponse(eq("idem-key-001"), any()))
                .thenReturn(Optional.of(cachedResult));

            // when
            PaymentResult result = paymentService.createPayment(createCommand);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("멱등성 키 획득 실패 - 처리 중인 요청")
        void idempotencyAcquireFailed() {
            // given
            when(idempotencyStore.getResponse(anyString(), any())).thenReturn(Optional.empty());
            when(idempotencyStore.tryAcquire(anyString())).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> paymentService.createPayment(createCommand))
                .isInstanceOf(DuplicatePaymentException.class);
        }
    }

    @Nested
    @DisplayName("결제 승인")
    class ApprovePayment {

        @BeforeEach
        void setUpApproval() {
            // lockManager mock 설정 (일부 테스트에서는 사용되지 않을 수 있음)
            lenient().doAnswer(invocation -> {
                Runnable action = invocation.getArgument(1);
                action.run();
                return null;
            }).when(lockManager).executeWithLock(anyString(), any(Runnable.class));
        }

        @Test
        @DisplayName("정상 승인 - Saga 성공 흐름")
        void approvePaymentSuccess() {
            // given
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(walletRepository.findByIdWithOptimisticLock(1L)).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenReturn(wallet);
            when(pgGateway.approve(anyString(), any())).thenReturn(PgResponse.success("PG-TX-001"));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
            when(ledgerDomainService.createPaymentEntries(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(mock(LedgerEntry.class), mock(LedgerEntry.class)));

            // when
            PaymentResult result = paymentService.approvePayment(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(result.getPgTransactionId()).isEqualTo("PG-TX-001");

            verify(pgGateway).approve(eq("tok_test_1111"), any());
            verify(ledgerEntryRepository).saveAll(any());
            verify(eventPublisher, atLeastOnce()).publish(any());
        }

        @Test
        @DisplayName("PG 실패 시 보상 트랜잭션 실행")
        void pgFailureTriggersSagaCompensation() {
            // given
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(walletRepository.findByIdWithOptimisticLock(1L)).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenReturn(wallet);
            when(pgGateway.approve(anyString(), any()))
                .thenReturn(PgResponse.failure("INSUFFICIENT_FUNDS", "잔액 부족"));
            when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));

            // when & then
            assertThatThrownBy(() -> paymentService.approvePayment(1L))
                .isInstanceOf(PgApprovalException.class);

            // 보상: 잔액 복구 호출 확인 (2번: 차감 + 복구)
            verify(walletRepository, atLeast(2)).findByIdWithOptimisticLock(1L);
        }

        @Test
        @DisplayName("존재하지 않는 결제 ID")
        void paymentNotFound() {
            // given
            when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.approvePayment(999L))
                .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("이미 승인된 결제는 현재 상태 반환")
        void alreadyApprovedReturnsCurrentState() {
            // given
            payment.approve("PG-TX-EXISTING");
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            // when
            PaymentResult result = paymentService.approvePayment(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            verify(pgGateway, never()).approve(any(), any());
        }
    }

    @Nested
    @DisplayName("결제 취소")
    class CancelPayment {

        @BeforeEach
        void setUpCancellation() {
            payment.approve("PG-TX-001"); // 승인 상태로 변경
            payment.clearDomainEvents();

            lenient().doAnswer(invocation -> {
                Runnable action = invocation.getArgument(1);
                action.run();
                return null;
            }).when(lockManager).executeWithLock(anyString(), any(Runnable.class));
        }

        @Test
        @DisplayName("정상 취소")
        void cancelPaymentSuccess() {
            // given
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(pgGateway.cancel(anyString(), any())).thenReturn(PgResponse.success("PGC-001"));
            when(walletRepository.findByIdWithOptimisticLock(1L)).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenReturn(wallet);
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
            when(ledgerDomainService.createCancellationEntries(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(mock(LedgerEntry.class), mock(LedgerEntry.class)));

            // when
            PaymentResult result = paymentService.cancelPayment(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

            verify(pgGateway).cancel(eq("PG-TX-001"), any());
            verify(ledgerEntryRepository).saveAll(any());
        }
    }

    @Nested
    @DisplayName("결제 조회")
    class GetPayment {

        @Test
        @DisplayName("정상 조회")
        void getPaymentSuccess() {
            // given
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            // when
            PaymentResult result = paymentService.getPayment(1L);

            // then
            assertThat(result.getOrderId()).isEqualTo("ORDER-001");
        }

        @Test
        @DisplayName("존재하지 않는 결제")
        void paymentNotFound() {
            // given
            when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.getPayment(999L))
                .isInstanceOf(PaymentNotFoundException.class);
        }
    }
}
