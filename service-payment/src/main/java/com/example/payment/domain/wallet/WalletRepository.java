package com.example.payment.domain.wallet;

import java.util.Optional;

public interface WalletRepository {

    Wallet save(Wallet wallet);

    Wallet saveAndFlush(Wallet wallet);

    Optional<Wallet> findById(Long id);

    Optional<Wallet> findByUserId(Long userId);

    Optional<Wallet> findByIdWithOptimisticLock(Long id);

    long count();
}
