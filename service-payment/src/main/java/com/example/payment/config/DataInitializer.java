package com.example.payment.config;

import com.example.payment.domain.Merchant;
import com.example.payment.domain.Wallet;
import com.example.payment.repository.MerchantRepository;
import com.example.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final WalletRepository walletRepository;
    private final MerchantRepository merchantRepository;

    @Override
    @Transactional
    public void run(String... args) {
        initializeWallets();
        initializeMerchants();
    }

    private void initializeWallets() {
        if (walletRepository.count() > 0) {
            log.info("Wallets already initialized");
            return;
        }

        // 테스트 사용자 1: 잔액 1,000,000원
        Wallet wallet1 = Wallet.builder()
            .userId(1L)
            .userName("테스트유저1")
            .balance(new BigDecimal("1000000"))
            .build();
        walletRepository.save(wallet1);

        // 테스트 사용자 2: 잔액 500,000원
        Wallet wallet2 = Wallet.builder()
            .userId(2L)
            .userName("테스트유저2")
            .balance(new BigDecimal("500000"))
            .build();
        walletRepository.save(wallet2);

        log.info("Initialized {} wallets", 2);
    }

    private void initializeMerchants() {
        if (merchantRepository.count() > 0) {
            log.info("Merchants already initialized");
            return;
        }

        // 테스트 가맹점 A: 수수료 2.5%
        Merchant merchant1 = Merchant.builder()
            .name("테스트가맹점A")
            .businessNumber("123-45-67890")
            .feeRate(new BigDecimal("0.0250"))
            .apiKey("test-api-key-merchant-a")
            .active(true)
            .build();
        merchantRepository.save(merchant1);

        // 테스트 가맹점 B: 수수료 2.0%
        Merchant merchant2 = Merchant.builder()
            .name("테스트가맹점B")
            .businessNumber("098-76-54321")
            .feeRate(new BigDecimal("0.0200"))
            .apiKey("test-api-key-merchant-b")
            .active(true)
            .build();
        merchantRepository.save(merchant2);

        log.info("Initialized {} merchants", 2);
    }
}
