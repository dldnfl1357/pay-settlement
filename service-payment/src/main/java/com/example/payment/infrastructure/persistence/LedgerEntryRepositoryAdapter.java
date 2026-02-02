package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.ledger.AccountType;
import com.example.payment.domain.ledger.LedgerEntry;
import com.example.payment.domain.ledger.LedgerEntryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LedgerEntryRepositoryAdapter implements LedgerEntryRepository {

    private final JpaLedgerEntryRepository jpaRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final String BATCH_INSERT_SQL =
        "INSERT INTO ledger_entries (transaction_id, payment_id, account_type, account_id, " +
        "entry_type, amount, balance_after, description, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public LedgerEntryRepositoryAdapter(JpaLedgerEntryRepository jpaRepository, JdbcTemplate jdbcTemplate) {
        this.jpaRepository = jpaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        return jpaRepository.save(entry);
    }

    @Override
    public List<LedgerEntry> saveAll(List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return entries;
        }

        jdbcTemplate.batchUpdate(BATCH_INSERT_SQL, entries, entries.size(),
            (ps, entry) -> {
                ps.setString(1, entry.getTransactionId().getValue());
                ps.setLong(2, entry.getPaymentId());
                ps.setString(3, entry.getAccountType().name());
                ps.setLong(4, entry.getAccountId());
                ps.setString(5, entry.getEntryType().name());
                ps.setBigDecimal(6, entry.getAmount().getAmount());
                ps.setBigDecimal(7, entry.getBalanceAfter() != null ? entry.getBalanceAfter().getAmount() : null);
                ps.setString(8, entry.getDescription());
                ps.setTimestamp(9, Timestamp.valueOf(entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now()));
            });

        return entries;
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
