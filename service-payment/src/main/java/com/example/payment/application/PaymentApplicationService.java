package com.example.payment.application;

import com.example.payment.application.dto.CreatePaymentCommand;
import com.example.payment.application.dto.PaymentResult;
import com.example.payment.application.exception.PaymentNotFoundException;
import com.example.payment.application.port.IdempotencyStore;
import com.example.payment.application.port.PaymentEventPublisher;
import com.example.payment.application.port.PgGateway;
import com.example.payment.domain.ledger.LedgerDomainService;
import com.example.payment.domain.ledger.LedgerEntry;
import com.example.payment.domain.ledger.LedgerEntryRepository;
import com.example.payment.domain.ledger.TransactionId;
import com.example.payment.domain.payment.*;
import com.example.payment.domain.payment.exception.DuplicatePaymentException;
import com.example.payment.domain.payment.exception.PaymentExpiredException;
import com.example.payment.domain.shared.DomainEvent;
import com.example.payment.domain.shared.Money;
import com.example.payment.infrastructure.metrics.PaymentMetrics;
import com.example.payment.infrastructure.pg.PgApprovalException;
import com.example.payment.infrastructure.pg.PgResponse;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentApplicationService {

    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PgGateway pgGateway;
    private final IdempotencyStore idempotencyStore;
    private final PaymentEventPublisher eventPublisher;
    private final LedgerDomainService ledgerDomainService;
    private final PaymentMetrics paymentMetrics;
    private final WalletService walletService;

    @Transactional
    public PaymentResult createPayment(CreatePaymentCommand command) {
        Optional<PaymentResult> cached = idempotencyStore.getResponse(
            command.getIdempotencyKey(), PaymentResult.class);
        if (cached.isPresent()) {
            paymentMetrics.incrementIdempotencyHit();
            return cached.get();
        }

        if (!idempotencyStore.tryAcquire(command.getIdempotencyKey())) {
            throw new DuplicatePaymentException("Payment request already in progress");
        }

        try {
            Payment payment = Payment.create(
                command.getWalletId(),
                command.getMerchantId(),
                OrderId.of(command.getOrderId()),
                Money.of(command.getAmount()),
                CardToken.of(command.getCardToken()),
                IdempotencyKey.of(command.getIdempotencyKey()),
                command.getProductName()
            );

            payment = paymentRepository.save(payment);
            paymentMetrics.incrementPaymentCreated();

            PaymentResult result = doApprove(payment);
            idempotencyStore.complete(command.getIdempotencyKey(), result);
            return result;

        } catch (Exception e) {
            idempotencyStore.release(command.getIdempotencyKey());
            throw e;
        }
    }

    @Transactional
    public PaymentResult approvePayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return PaymentResult.from(payment);
        }

        return doApprove(payment);
    }

    private PaymentResult doApprove(Payment payment) {
        Timer.Sample approvalTimer = paymentMetrics.startTimer();
        Long paymentId = payment.getId();

        if (payment.isExpired()) {
            payment.fail("Payment expired");
            paymentRepository.save(payment);
            publishEvents(payment);
            // 메트릭은 GlobalExceptionHandler에서 수집
            throw new PaymentExpiredException("Payment has expired: " + paymentId);
        }

        boolean balanceDeducted = false;
        Money walletBalanceAfterDeduct = null;

        try {
            walletBalanceAfterDeduct = deductWalletBalance(payment.getWalletId(), payment.getAmount());
            balanceDeducted = true;

            Timer.Sample pgTimer = paymentMetrics.startTimer();
            PgResponse pgResponse = pgGateway.approve(
                payment.getCardToken().getValue(), payment.getAmount().getAmount());
            paymentMetrics.stopPgCallTimer(pgTimer);

            if (!pgResponse.isSuccess()) {
                throw new PgApprovalException(pgResponse.getErrorCode(), pgResponse.getErrorMessage());
            }

            payment.approve(pgResponse.getTransactionId());
            payment = paymentRepository.save(payment);

            recordPaymentLedger(payment, walletBalanceAfterDeduct);

            publishEvents(payment);

            paymentMetrics.incrementPaymentApproved();
            paymentMetrics.recordPaymentAmount(payment.getAmount().getAmount(), "APPROVED");
            paymentMetrics.stopApprovalTimer(approvalTimer);
            return PaymentResult.from(payment);

        } catch (Exception e) {
            compensate(payment, balanceDeducted);
            throw e;
        }
    }

    @Transactional
    public PaymentResult cancelPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        PgResponse pgResponse = pgGateway.cancel(
            payment.getPgTransactionId(), payment.getAmount().getAmount());

        if (!pgResponse.isSuccess()) {
            throw new RuntimeException("PG cancellation failed: " + pgResponse.getErrorMessage());
        }

        // 잔액 복구 (복구 후 잔액 반환)
        Money walletBalance = restoreWalletBalance(payment.getWalletId(), payment.getAmount());

        // 결제 취소
        payment.cancel();
        payment = paymentRepository.save(payment);

        // 취소 원장 기록 (이미 조회한 잔액 재사용)
        recordCancellationLedger(payment, walletBalance);

        publishEvents(payment);

        paymentMetrics.incrementPaymentCancelled();
        paymentMetrics.recordPaymentAmount(payment.getAmount().getAmount(), "CANCELLED");
        return PaymentResult.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResult getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return PaymentResult.from(payment);
    }

    private Money deductWalletBalance(Long walletId, Money amount) {
        return walletService.deduct(walletId, amount);
    }

    private Money restoreWalletBalance(Long walletId, Money amount) {
        return walletService.restore(walletId, amount);
    }

    private void recordPaymentLedger(Payment payment, Money walletBalance) {
        List<LedgerEntry> entries = ledgerDomainService.createPaymentEntries(
            TransactionId.generate(), payment.getId(),
            payment.getWalletId(), payment.getMerchantId(),
            payment.getAmount(), walletBalance,
            payment.getOrderId().getValue()
        );
        ledgerEntryRepository.saveAll(entries);
    }

    private void recordCancellationLedger(Payment payment, Money walletBalance) {
        List<LedgerEntry> entries = ledgerDomainService.createCancellationEntries(
            TransactionId.generate(), payment.getId(),
            payment.getWalletId(), payment.getMerchantId(),
            payment.getAmount(), walletBalance,
            payment.getOrderId().getValue()
        );
        ledgerEntryRepository.saveAll(entries);
    }

    private void publishEvents(Payment payment) {
        for (DomainEvent event : payment.getDomainEvents()) {
            eventPublisher.publish(event);
        }
        payment.clearDomainEvents();
    }

    private void compensate(Payment payment, boolean balanceDeducted) {
        paymentMetrics.incrementSagaCompensation();
        try {
            if (balanceDeducted) {
                Money walletBalance = restoreWalletBalance(payment.getWalletId(), payment.getAmount());

                LedgerEntry compensationEntry = ledgerDomainService.createCompensationEntry(
                    TransactionId.generate(), payment.getId(),
                    payment.getWalletId(), payment.getAmount(),
                    walletBalance, payment.getOrderId().getValue()
                );
                ledgerEntryRepository.save(compensationEntry);
            }

            payment.fail("Approval failed - compensated");
            paymentRepository.save(payment);

            publishEvents(payment);
        } catch (Exception e) {
            log.error("Compensation failed: id={}", payment.getId(), e);
        }
    }
}
