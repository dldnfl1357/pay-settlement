package com.example.payment.domain.wallet;

import com.example.payment.domain.shared.BaseAggregateRoot;
import com.example.payment.domain.shared.Money;
import com.example.payment.domain.wallet.exception.InsufficientBalanceException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallets", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet extends BaseAggregateRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "balance", precision = 15, scale = 2, nullable = false))
    private Money balance;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Wallet create(Long userId, String userName, Money initialBalance) {
        Wallet wallet = new Wallet();
        wallet.userId = userId;
        wallet.userName = userName;
        wallet.balance = initialBalance != null ? initialBalance : Money.zero();
        wallet.createdAt = LocalDateTime.now();
        wallet.updatedAt = wallet.createdAt;
        return wallet;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = this.createdAt;
        if (this.balance == null) {
            this.balance = Money.zero();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void deduct(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (!this.balance.isGreaterThanOrEqual(amount)) {
            throw new InsufficientBalanceException(
                "Insufficient balance. Current: " + this.balance + ", Required: " + amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void restore(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }

    public boolean hasEnoughBalance(Money amount) {
        return this.balance.isGreaterThanOrEqual(amount);
    }
}
