package com.example.payment.domain.ledger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface LedgerEntryRepository {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> saveAll(List<LedgerEntry> entries);

    List<LedgerEntry> findByTransactionId(String transactionId);

    List<LedgerEntry> findByPaymentId(Long paymentId);

    List<LedgerEntry> findByAccountTypeAndAccountId(AccountType accountType, Long accountId);

    BigDecimal calculateBalance(AccountType accountType, Long accountId);

    List<LedgerEntry> findByAccountAndPeriod(
        AccountType accountType, Long accountId,
        LocalDateTime startDate, LocalDateTime endDate);
}
