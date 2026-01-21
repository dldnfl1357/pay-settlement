package com.example.payment.service;

import com.example.payment.controller.dto.PaymentRequest;
import com.example.payment.controller.dto.PaymentResponse;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.event.PaymentEventPublisher;
import com.example.payment.infrastructure.pg.MockPgClient;
import com.example.payment.infrastructure.pg.PgApprovalException;
import com.example.payment.infrastructure.pg.PgResponse;
import com.example.payment.infrastructure.redis.IdempotencyService;
import com.example.payment.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaService {

    private final PaymentRepository paymentRepository;
    private final WalletService walletService;
    private final LedgerService ledgerService;
    private final MockPgClient pgClient;
    private final IdempotencyService idempotencyService;
    private final PaymentEventPublisher eventPublisher;

    /**
     * 결제 요청 생성
     */
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        // 멱등성 체크
        Optional<PaymentResponse> cached = idempotencyService.getResponse(
            request.getIdempotencyKey(), PaymentResponse.class);
        if (cached.isPresent()) {
            log.info("Idempotency hit: returning cached response for key={}",
                request.getIdempotencyKey());
            return cached.get();
        }

        // 멱등성 키 획득 시도
        if (!idempotencyService.tryAcquire(request.getIdempotencyKey())) {
            // 이미 처리 중인 요청
            log.warn("Duplicate request in progress: key={}", request.getIdempotencyKey());
            throw new DuplicatePaymentException("Payment request already in progress");
        }

        try {
            // 결제 생성
            Payment payment = Payment.builder()
                .walletId(request.getWalletId())
                .merchantId(request.getMerchantId())
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .cardToken(request.getCardToken())
                .productName(request.getProductName())
                .idempotencyKey(request.getIdempotencyKey())
                .status(PaymentStatus.PENDING)
                .build();

            payment = paymentRepository.save(payment);

            log.info("Payment created: id={}, orderId={}, amount={}",
                payment.getId(), payment.getOrderId(), payment.getAmount());

            eventPublisher.publishRequested(payment);

            PaymentResponse response = PaymentResponse.from(payment);
            idempotencyService.complete(request.getIdempotencyKey(), response);

            return response;

        } catch (Exception e) {
            idempotencyService.release(request.getIdempotencyKey());
            throw e;
        }
    }

    /**
     * 결제 승인 (Saga 패턴)
     */
    @Transactional
    public PaymentResponse approvePayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("Invalid payment status for approval: id={}, status={}",
                paymentId, payment.getStatus());
            return PaymentResponse.from(payment);
        }

        if (payment.isExpired()) {
            payment.fail();
            paymentRepository.save(payment);
            throw new PaymentExpiredException("Payment has expired: " + paymentId);
        }

        boolean balanceDeducted = false;

        try {
            // Step 1: 잔액 확인 및 차감
            log.info("Step 1: Deducting balance - paymentId={}, walletId={}, amount={}",
                paymentId, payment.getWalletId(), payment.getAmount());
            walletService.deduct(payment.getWalletId(), payment.getAmount());
            balanceDeducted = true;

            // Step 2: PG 승인
            log.info("Step 2: PG approval - paymentId={}", paymentId);
            PgResponse pgResponse = pgClient.approve(payment.getCardToken(), payment.getAmount());

            if (!pgResponse.isSuccess()) {
                throw new PgApprovalException(
                    pgResponse.getErrorCode(),
                    pgResponse.getErrorMessage()
                );
            }

            // Step 3: 결제 완료 처리
            log.info("Step 3: Completing payment - paymentId={}, pgTxId={}",
                paymentId, pgResponse.getTransactionId());
            payment.approve(pgResponse.getTransactionId());
            payment = paymentRepository.save(payment);

            // Step 4: 원장 기록
            log.info("Step 4: Recording ledger - paymentId={}", paymentId);
            ledgerService.recordPayment(payment);

            // Step 5: 이벤트 발행
            log.info("Step 5: Publishing event - paymentId={}", paymentId);
            eventPublisher.publishApproved(payment);

            log.info("Payment approved successfully: id={}", paymentId);
            return PaymentResponse.from(payment);

        } catch (Exception e) {
            // 보상 트랜잭션 실행
            log.error("Payment approval failed, starting compensation: paymentId={}, error={}",
                paymentId, e.getMessage());
            compensate(payment, balanceDeducted);
            throw e;
        }
    }

    /**
     * 결제 취소
     */
    @Transactional
    public PaymentResponse cancelPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                "Only approved payments can be cancelled. Current status: " + payment.getStatus());
        }

        // PG 취소
        log.info("Cancelling PG transaction: paymentId={}, pgTxId={}",
            paymentId, payment.getPgTransactionId());
        PgResponse pgResponse = pgClient.cancel(
            payment.getPgTransactionId(), payment.getAmount());

        if (!pgResponse.isSuccess()) {
            throw new RuntimeException("PG cancellation failed: " + pgResponse.getErrorMessage());
        }

        // 잔액 복구
        log.info("Restoring balance: paymentId={}, walletId={}, amount={}",
            paymentId, payment.getWalletId(), payment.getAmount());
        walletService.restore(payment.getWalletId(), payment.getAmount());

        // 결제 취소 처리
        payment.cancel();
        payment = paymentRepository.save(payment);

        // 취소 원장 기록
        ledgerService.recordCancellation(payment);

        // 이벤트 발행
        eventPublisher.publishCancelled(payment);

        log.info("Payment cancelled successfully: id={}", paymentId);
        return PaymentResponse.from(payment);
    }

    /**
     * 결제 조회
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        return PaymentResponse.from(payment);
    }

    /**
     * 보상 트랜잭션
     */
    private void compensate(Payment payment, boolean balanceDeducted) {
        log.warn("Starting Saga compensation: paymentId={}", payment.getId());

        try {
            if (balanceDeducted) {
                // 잔액 복구
                log.info("Compensating: restoring balance - walletId={}, amount={}",
                    payment.getWalletId(), payment.getAmount());
                walletService.restore(payment.getWalletId(), payment.getAmount());

                // 보상 원장 기록
                ledgerService.recordCompensation(payment);
            }

            // 결제 실패 처리
            payment.fail();
            paymentRepository.save(payment);

            // 실패 이벤트 발행
            eventPublisher.publishFailed(payment);

            log.info("Saga compensation completed: paymentId={}", payment.getId());

        } catch (Exception e) {
            log.error("Saga compensation failed: paymentId={}, error={}",
                payment.getId(), e.getMessage(), e);
            // 보상 실패 시 알림 또는 수동 처리 필요
        }
    }
}
