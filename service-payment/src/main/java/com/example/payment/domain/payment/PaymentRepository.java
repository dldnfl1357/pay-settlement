package com.example.payment.domain.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<Payment> findByMerchantIdAndStatusAndCreatedAtBetween(
        Long merchantId, PaymentStatus status, LocalDateTime startDate, LocalDateTime endDate);

    List<Payment> findByStatusAndCreatedAtBetween(
        PaymentStatus status, LocalDateTime startDate, LocalDateTime endDate);
}
