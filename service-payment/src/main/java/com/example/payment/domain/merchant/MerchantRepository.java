package com.example.payment.domain.merchant;

import java.util.List;
import java.util.Optional;

public interface MerchantRepository {

    Merchant save(Merchant merchant);

    Optional<Merchant> findById(Long id);

    List<Merchant> findAll();

    long count();
}
