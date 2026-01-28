package com.example.payment.domain.merchant;

import java.util.List;
import java.util.Optional;

public interface MerchantRepository {

    Merchant save(Merchant merchant);

    Optional<Merchant> findById(Long id);

    Optional<Merchant> findByApiKey(String apiKey);

    List<Merchant> findByActiveTrue();

    long count();
}
