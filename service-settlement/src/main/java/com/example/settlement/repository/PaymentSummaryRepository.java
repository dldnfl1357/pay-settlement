package com.example.settlement.repository;

import com.example.settlement.domain.PaymentSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentSummaryRepository extends JpaRepository<PaymentSummary, Long> {

    Optional<PaymentSummary> findByPaymentId(Long paymentId);

    List<PaymentSummary> findByMerchantIdAndPaymentDateAndSettledFalse(
        Long merchantId, LocalDate paymentDate);

    @Query("SELECT DISTINCT ps.merchantId FROM PaymentSummary ps " +
           "WHERE ps.paymentDate = :date AND ps.settled = false")
    List<Long> findDistinctMerchantIdsByPaymentDateAndSettledFalse(@Param("date") LocalDate date);

    @Query("SELECT ps FROM PaymentSummary ps " +
           "WHERE ps.paymentDate = :date AND ps.settled = false")
    List<PaymentSummary> findUnsettledByDate(@Param("date") LocalDate date);

    @Modifying
    @Query("UPDATE PaymentSummary ps SET ps.settled = true " +
           "WHERE ps.merchantId = :merchantId AND ps.paymentDate = :date AND ps.settled = false")
    int markAsSettled(@Param("merchantId") Long merchantId, @Param("date") LocalDate date);
}
