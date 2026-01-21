package com.example.settlement.repository;

import com.example.settlement.domain.Settlement;
import com.example.settlement.domain.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByMerchantIdAndSettlementDate(Long merchantId, LocalDate settlementDate);

    List<Settlement> findByMerchantId(Long merchantId);

    List<Settlement> findByStatus(SettlementStatus status);

    List<Settlement> findBySettlementDateBetween(LocalDate startDate, LocalDate endDate);

    List<Settlement> findByMerchantIdAndSettlementDateBetween(
        Long merchantId, LocalDate startDate, LocalDate endDate);
}
