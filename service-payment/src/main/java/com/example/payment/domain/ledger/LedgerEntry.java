package com.example.payment.domain.ledger;

import com.example.payment.domain.shared.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_payment_id", columnList = "payment_id"),
    @Index(name = "idx_account", columnList = "account_type, account_id")
})
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private TransactionId transactionId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private EntryType entryType;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", precision = 15, scale = 2, nullable = false))
    private Money amount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "balance_after", precision = 15, scale = 2))
    private Money balanceAfter;

    @Column(length = 200)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static LedgerEntry create(TransactionId transactionId, Long paymentId,
                                     AccountType accountType, Long accountId,
                                     EntryType entryType, Money amount,
                                     Money balanceAfter, String description) {
        LedgerEntry entry = new LedgerEntry();
        entry.transactionId = transactionId;
        entry.paymentId = paymentId;
        entry.accountType = accountType;
        entry.accountId = accountId;
        entry.entryType = entryType;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.description = description;
        entry.createdAt = LocalDateTime.now();
        return entry;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public boolean isDebit() {
        return this.entryType == EntryType.DEBIT;
    }

    public boolean isCredit() {
        return this.entryType == EntryType.CREDIT;
    }
}
