package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.ledger.AccountType;
import com.example.payment.domain.ledger.LedgerEntry;
import com.example.payment.domain.ledger.LedgerEntryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface JpaLedgerEntryRepository extends JpaRepository<LedgerEntry, Long>, LedgerEntryRepository {

    @Query("SELECT e FROM LedgerEntry e WHERE e.transactionId.value = :txId")
    List<LedgerEntry> findByTransactionId(@Param("txId") String transactionId);

    List<LedgerEntry> findByPaymentId(Long paymentId);

    List<LedgerEntry> findByAccountTypeAndAccountId(AccountType accountType, Long accountId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount.amount ELSE -e.amount.amount END), 0) " +
           "FROM LedgerEntry e WHERE e.accountType = :accountType AND e.accountId = :accountId")
    BigDecimal calculateBalance(
        @Param("accountType") AccountType accountType,
        @Param("accountId") Long accountId);

    @Query("SELECT e FROM LedgerEntry e WHERE e.accountType = :accountType " +
           "AND e.accountId = :accountId AND e.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY e.createdAt DESC")
    List<LedgerEntry> findByAccountAndPeriod(
        @Param("accountType") AccountType accountType,
        @Param("accountId") Long accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
}
