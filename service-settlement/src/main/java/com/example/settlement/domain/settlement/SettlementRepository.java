package com.example.settlement.domain.settlement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository {

    Settlement save(Settlement settlement);

    Optional<Settlement> findById(Long id);

    Optional<Settlement> findByMerchantIdAndSettlementDate(Long merchantId, LocalDate settlementDate);

    List<Settlement> findByMerchantId(Long merchantId);

    List<Settlement> findByStatus(SettlementStatus status);

    List<Settlement> findBySettlementDateBetween(LocalDate startDate, LocalDate endDate);

    List<Settlement> findByMerchantIdAndSettlementDateBetween(
        Long merchantId, LocalDate startDate, LocalDate endDate);

    List<Settlement> findAll();
}
