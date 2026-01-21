package com.example.payment.repository;

import com.example.payment.domain.AccountType;
import com.example.payment.domain.EntryType;
import com.example.payment.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(String transactionId);

    List<LedgerEntry> findByPaymentId(Long paymentId);

    List<LedgerEntry> findByAccountTypeAndAccountId(AccountType accountType, Long accountId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) " +
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

    @Query("SELECT SUM(e.amount) FROM LedgerEntry e WHERE e.entryType = :entryType")
    BigDecimal sumByEntryType(@Param("entryType") EntryType entryType);
}
