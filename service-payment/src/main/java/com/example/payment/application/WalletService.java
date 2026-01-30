package com.example.payment.application;

import com.example.payment.domain.shared.Money;
import com.example.payment.domain.wallet.Wallet;
import com.example.payment.domain.wallet.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional
    public void deduct(Long walletId, Money amount) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + walletId));
        wallet.deduct(amount);
        walletRepository.save(wallet);
    }

    @Transactional
    public void restore(Long walletId, Money amount) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + walletId));
        wallet.restore(amount);
        walletRepository.save(wallet);
    }

    @Transactional(readOnly = true)
    public Money getBalance(Long walletId) {
        return walletRepository.findById(walletId)
            .map(Wallet::getBalance)
            .orElse(Money.zero());
    }
}
