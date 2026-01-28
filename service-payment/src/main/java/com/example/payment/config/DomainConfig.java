package com.example.payment.config;

import com.example.payment.domain.ledger.LedgerDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public LedgerDomainService ledgerDomainService() {
        return new LedgerDomainService();
    }
}
