package com.example.settlement.domain.paymentsummary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentSummaryRepository {

    PaymentSummary save(PaymentSummary summary);

    Optional<PaymentSummary> findByPaymentId(Long paymentId);

    List<PaymentSummary> findUnsettledByDate(LocalDate date);

    int markAsSettled(Long merchantId, LocalDate date);
}
