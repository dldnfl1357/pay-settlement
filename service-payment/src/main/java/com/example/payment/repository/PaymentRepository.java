package com.example.payment.repository;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByMerchantIdAndCreatedAtBetween(
        Long merchantId, LocalDateTime startDate, LocalDateTime endDate);

    List<Payment> findByMerchantIdAndStatus(Long merchantId, PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.merchantId = :merchantId " +
           "AND p.status = :status AND p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByMerchantAndStatusAndPeriod(
        @Param("merchantId") Long merchantId,
        @Param("status") PaymentStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT p FROM Payment p WHERE p.status = :status " +
           "AND p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByStatusAndPeriod(
        @Param("status") PaymentStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
