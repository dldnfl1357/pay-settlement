package com.example.settlement.application;

import com.example.settlement.application.dto.SettlementResult;
import com.example.settlement.domain.settlement.FeeRate;
import com.example.settlement.domain.settlement.Settlement;
import com.example.settlement.domain.settlement.SettlementRepository;
import com.example.settlement.domain.settlement.SettlementStatus;
import com.example.settlement.domain.shared.Money;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementApplicationService 테스트")
class SettlementApplicationServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @InjectMocks
    private SettlementApplicationService settlementService;

    private Settlement settlement;

    @BeforeEach
    void setUp() {
        settlement = Settlement.createFromSummary(
            1L,
            LocalDate.of(2024, 1, 15),
            Money.of(100000),
            Money.of(10000),
            FeeRate.of("0.025"),
            5
        );
    }

    @Nested
    @DisplayName("조회")
    class Query {

        @Test
        @DisplayName("가맹점 ID로 조회")
        void getSettlementsByMerchantId() {
            when(settlementRepository.findByMerchantId(1L))
                .thenReturn(List.of(settlement));

            List<SettlementResult> results = settlementService.getSettlements(1L, null, null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMerchantId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("기간으로 조회")
        void getSettlementsByDateRange() {
            LocalDate start = LocalDate.of(2024, 1, 1);
            LocalDate end = LocalDate.of(2024, 1, 31);

            when(settlementRepository.findBySettlementDateBetween(start, end))
                .thenReturn(List.of(settlement));

            List<SettlementResult> results = settlementService.getSettlements(null, start, end);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("단건 조회")
        void getSettlementById() {
            when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));

            SettlementResult result = settlementService.getSettlement(1L);

            assertThat(result.getTotalSales()).isEqualByComparingTo("100000");
            assertThat(result.getNetAmount()).isEqualByComparingTo("87500");
        }

        @Test
        @DisplayName("존재하지 않는 정산 조회")
        void getSettlementNotFound() {
            when(settlementRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> settlementService.getSettlement(999L))
                .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("상태 변경")
    class StateChange {

        @Test
        @DisplayName("정산 확정")
        void confirmSettlement() {
            when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
            when(settlementRepository.save(any())).thenReturn(settlement);

            SettlementResult result = settlementService.confirmSettlement(1L);

            assertThat(result.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
            verify(settlementRepository).save(any());
        }

        @Test
        @DisplayName("지급 완료 처리")
        void markAsPaid() {
            settlement.confirm(); // 먼저 확정
            when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
            when(settlementRepository.save(any())).thenReturn(settlement);

            SettlementResult result = settlementService.markAsPaid(1L);

            assertThat(result.getStatus()).isEqualTo(SettlementStatus.PAID);
        }
    }
}
