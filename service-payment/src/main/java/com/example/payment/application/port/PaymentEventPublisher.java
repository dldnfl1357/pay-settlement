package com.example.payment.application.port;

import com.example.payment.domain.shared.DomainEvent;

public interface PaymentEventPublisher {

    void publish(DomainEvent event);
}
