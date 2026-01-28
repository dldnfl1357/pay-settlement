package com.example.settlement.infrastructure.acl;

import com.example.settlement.domain.paymentsummary.PaymentSummary;
import com.example.settlement.domain.paymentsummary.PaymentSummaryStatus;
import com.example.settlement.domain.shared.Money;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventTranslator {

    public PaymentSummary toApprovedSummary(ExternalPaymentEvent event) {
        ExternalPaymentEvent.Payload p = event.getPayload();
        return PaymentSummary.create(
            p.getPaymentId(),
            p.getMerchantId(),
            p.getWalletId(),
            p.getOrderId(),
            Money.of(p.getAmount()),
            PaymentSummaryStatus.APPROVED,
            event.getTimestamp().toLocalDate()
        );
    }

    public PaymentSummary toCancelledSummary(ExternalPaymentEvent event) {
        ExternalPaymentEvent.Payload p = event.getPayload();
        return PaymentSummary.create(
            -p.getPaymentId(),  // 음수로 구분
            p.getMerchantId(),
            p.getWalletId(),
            p.getOrderId(),
            Money.of(p.getAmount()),
            PaymentSummaryStatus.CANCELLED,
            event.getTimestamp().toLocalDate()
        );
    }
}
