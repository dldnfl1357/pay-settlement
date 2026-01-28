package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JpaPaymentRepository extends JpaRepository<Payment, Long>, PaymentRepository {

    @Query("SELECT p FROM Payment p WHERE p.idempotencyKey.value = :key")
    Optional<Payment> findByIdempotencyKey(@Param("key") String key);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Payment p WHERE p.idempotencyKey.value = :key")
    boolean existsByIdempotencyKey(@Param("key") String key);

    @Query("SELECT p FROM Payment p WHERE p.merchantId = :merchantId " +
           "AND p.status = :status AND p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByMerchantIdAndStatusAndCreatedAtBetween(
        @Param("merchantId") Long merchantId,
        @Param("status") PaymentStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT p FROM Payment p WHERE p.status = :status " +
           "AND p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByStatusAndCreatedAtBetween(
        @Param("status") PaymentStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
}
