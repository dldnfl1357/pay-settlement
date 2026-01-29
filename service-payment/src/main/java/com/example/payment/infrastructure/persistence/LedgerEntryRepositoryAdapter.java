package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.ledger.AccountType;
import com.example.payment.domain.ledger.LedgerEntry;
import com.example.payment.domain.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LedgerEntryRepositoryAdapter implements LedgerEntryRepository {

    private final JpaLedgerEntryRepository jpaRepository;

    public LedgerEntryRepositoryAdapter(JpaLedgerEntryRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        return jpaRepository.save(entry);
    }

    @Override
    public List<LedgerEntry> saveAll(List<LedgerEntry> entries) {
        return jpaRepository.saveAll(entries);
    }

    @Override
    public List<LedgerEntry> findByTransactionId(String transactionId) {
        return jpaRepository.findByTransactionIdValue(transactionId);
    }

    @Override
    public List<LedgerEntry> findByPaymentId(Long paymentId) {
        return jpaRepository.findByPaymentId(paymentId);
    }

    @Override
    public List<LedgerEntry> findByAccountTypeAndAccountId(AccountType accountType, Long accountId) {
        return jpaRepository.findByAccountTypeAndAccountId(accountType, accountId);
    }

    @Override
    public BigDecimal calculateBalance(AccountType accountType, Long accountId) {
        return jpaRepository.calculateBalance(accountType, accountId);
    }

    @Override
    public List<LedgerEntry> findByAccountAndPeriod(
            AccountType accountType, Long accountId,
            LocalDateTime startDate, LocalDateTime endDate) {
        return jpaRepository.findByAccountAndPeriod(accountType, accountId, startDate, endDate);
    }
}
