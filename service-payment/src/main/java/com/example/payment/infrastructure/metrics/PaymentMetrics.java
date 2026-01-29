package com.example.payment.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter paymentCreatedCounter;
    private final Counter paymentApprovedCounter;
    private final Counter paymentFailedCounter;
    private final Counter paymentCancelledCounter;
    private final Counter idempotencyHitCounter;
    private final Counter sagaCompensationCounter;
    private final Counter lockAcquisitionFailureCounter;

    // Timers
    private final Timer paymentApprovalTimer;
    private final Timer pgCallTimer;
    private final Timer lockAcquisitionTimer;

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Payment counters
        this.paymentCreatedCounter = Counter.builder("payment.created.total")
            .description("Total number of payments created")
            .register(meterRegistry);

        this.paymentApprovedCounter = Counter.builder("payment.approved.total")
            .description("Total number of payments approved")
            .register(meterRegistry);

        this.paymentFailedCounter = Counter.builder("payment.failed.total")
            .description("Total number of payments failed")
            .register(meterRegistry);

        this.paymentCancelledCounter = Counter.builder("payment.cancelled.total")
            .description("Total number of payments cancelled")
            .register(meterRegistry);

        // Idempotency
        this.idempotencyHitCounter = Counter.builder("payment.idempotency.hit.total")
            .description("Total number of idempotency cache hits")
            .register(meterRegistry);

        // Saga
        this.sagaCompensationCounter = Counter.builder("payment.saga.compensation.total")
            .description("Total number of saga compensations executed")
            .register(meterRegistry);

        // Lock
        this.lockAcquisitionFailureCounter = Counter.builder("payment.lock.failure.total")
            .description("Total number of lock acquisition failures")
            .register(meterRegistry);

        // Timers
        this.paymentApprovalTimer = Timer.builder("payment.approval.duration")
            .description("Time taken to approve a payment")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.pgCallTimer = Timer.builder("payment.pg.call.duration")
            .description("Time taken for PG API call")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.lockAcquisitionTimer = Timer.builder("payment.lock.acquisition.duration")
            .description("Time taken to acquire distributed lock")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }

    // Counter methods
    public void incrementPaymentCreated() {
        paymentCreatedCounter.increment();
    }

    public void incrementPaymentApproved() {
        paymentApprovedCounter.increment();
    }

    public void incrementPaymentFailed() {
        paymentFailedCounter.increment();
    }

    public void incrementPaymentCancelled() {
        paymentCancelledCounter.increment();
    }

    public void incrementIdempotencyHit() {
        idempotencyHitCounter.increment();
    }

    public void incrementSagaCompensation() {
        sagaCompensationCounter.increment();
    }

    public void incrementLockAcquisitionFailure() {
        lockAcquisitionFailureCounter.increment();
    }

    // Timer methods
    public void recordPaymentApprovalTime(long durationMs) {
        paymentApprovalTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordPgCallTime(long durationMs) {
        pgCallTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordLockAcquisitionTime(long durationMs) {
        lockAcquisitionTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    // Gauge for payment amount (use separately for real-time tracking)
    public void recordPaymentAmount(BigDecimal amount, String status) {
        meterRegistry.counter("payment.amount.total",
            "status", status
        ).increment(amount.doubleValue());
    }

    // Timer wrapper for code blocks
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopApprovalTimer(Timer.Sample sample) {
        sample.stop(paymentApprovalTimer);
    }

    public void stopPgCallTimer(Timer.Sample sample) {
        sample.stop(pgCallTimer);
    }

    public void stopLockAcquisitionTimer(Timer.Sample sample) {
        sample.stop(lockAcquisitionTimer);
    }
}
