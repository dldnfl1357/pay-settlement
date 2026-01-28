package com.example.payment.application.port;

import java.util.function.Supplier;

public interface DistributedLockManager {

    <T> T executeWithLock(String key, Supplier<T> action);

    void executeWithLock(String key, Runnable action);
}
