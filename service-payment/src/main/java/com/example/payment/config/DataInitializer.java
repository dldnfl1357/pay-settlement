package com.example.payment.config;

import com.example.payment.domain.merchant.FeeRate;
import com.example.payment.domain.merchant.Merchant;
import com.example.payment.domain.merchant.MerchantRepository;
import com.example.payment.domain.shared.Money;
import com.example.payment.domain.wallet.Wallet;
import com.example.payment.domain.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

        Wallet wallet1 = Wallet.create(1L, "테스트유저1", Money.of("1000000"));
        walletRepository.save(wallet1);

        Wallet wallet2 = Wallet.create(2L, "테스트유저2", Money.of("500000"));
        walletRepository.save(wallet2);

        log.info("Initialized {} wallets", 2);
    }

    private void initializeMerchants() {
        if (merchantRepository.count() > 0) {
            log.info("Merchants already initialized");
            return;
        }

        Merchant merchant1 = Merchant.create(
            "테스트가맹점A", "123-45-67890", FeeRate.of("0.0250")
        );
        merchantRepository.save(merchant1);

        Merchant merchant2 = Merchant.create(
            "테스트가맹점B", "098-76-54321", FeeRate.of("0.0200")
        );
        merchantRepository.save(merchant2);

        log.info("Initialized {} merchants", 2);
    }
}
