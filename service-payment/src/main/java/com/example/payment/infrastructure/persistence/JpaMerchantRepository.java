package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.merchant.Merchant;
import com.example.payment.domain.merchant.MerchantRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaMerchantRepository extends JpaRepository<Merchant, Long>, MerchantRepository {

    Optional<Merchant> findByApiKey(String apiKey);

    List<Merchant> findByActiveTrue();
}
