package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.wallet.Wallet;
import com.example.payment.domain.wallet.WalletRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JpaWalletRepository extends JpaRepository<Wallet, Long>, WalletRepository {

    Optional<Wallet> findByUserId(Long userId);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithOptimisticLock(@Param("id") Long id);
}
