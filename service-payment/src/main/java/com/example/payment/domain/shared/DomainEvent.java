package com.example.payment.domain.shared;

import java.time.LocalDateTime;

public interface DomainEvent {

    String getEventId();

    LocalDateTime getOccurredAt();
}
