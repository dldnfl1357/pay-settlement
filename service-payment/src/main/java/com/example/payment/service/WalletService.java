package com.example.payment.service;

import com.example.payment.domain.Wallet;
import com.example.payment.infrastructure.redis.DistributedLockService;
import com.example.payment.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final DistributedLockService lockService;

    @Transactional(readOnly = true)
    public Wallet getWallet(Long walletId) {
        return walletRepository.findById(walletId)
            .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + walletId));
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long walletId) {
        return getWallet(walletId).getBalance();
    }

    @Transactional(readOnly = true)
    public boolean hasEnoughBalance(Long walletId, BigDecimal amount) {
        return getWallet(walletId).hasEnoughBalance(amount);
    }

    /**
     * 분산 락과 낙관적 잠금을 사용하여 잔액 차감
     */
    @Transactional
    public void deduct(Long walletId, BigDecimal amount) {
        lockService.executeWithLock(
            DistributedLockService.walletLockKey(walletId),
            () -> {
                Wallet wallet = walletRepository.findByIdWithOptimisticLock(walletId)
                    .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + walletId));

                log.info("Deducting from wallet: walletId={}, amount={}, currentBalance={}",
                    walletId, amount, wallet.getBalance());

                wallet.deduct(amount);
                walletRepository.save(wallet);

                log.info("Deduction complete: walletId={}, newBalance={}",
                    walletId, wallet.getBalance());

                return null;
            }
        );
    }

    /**
     * 분산 락을 사용하여 잔액 복구 (보상 트랜잭션)
     */
    @Transactional
    public void restore(Long walletId, BigDecimal amount) {
        lockService.executeWithLock(
            DistributedLockService.walletLockKey(walletId),
            () -> {
                Wallet wallet = walletRepository.findByIdWithOptimisticLock(walletId)
                    .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + walletId));

                log.info("Restoring to wallet: walletId={}, amount={}, currentBalance={}",
                    walletId, amount, wallet.getBalance());

                wallet.restore(amount);
                walletRepository.save(wallet);

                log.info("Restoration complete: walletId={}, newBalance={}",
                    walletId, wallet.getBalance());

                return null;
            }
        );
    }
}
