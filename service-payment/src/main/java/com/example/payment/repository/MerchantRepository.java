package com.example.payment.repository;

import com.example.payment.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByApiKey(String apiKey);

    List<Merchant> findByActiveTrue();
}
