package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.merchant.Merchant;
import com.example.payment.domain.merchant.MerchantRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMerchantRepository extends JpaRepository<Merchant, Long>, MerchantRepository {
}
