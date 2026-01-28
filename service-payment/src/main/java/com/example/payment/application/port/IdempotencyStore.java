package com.example.payment.application.port;

import java.util.Optional;

public interface IdempotencyStore {

    <T> Optional<T> getResponse(String key, Class<T> responseType);

    boolean tryAcquire(String key);

    <T> void complete(String key, T response);

    void release(String key);
}
