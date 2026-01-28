package com.example.payment.infrastructure.redis;

import com.example.payment.application.port.DistributedLockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedissonLockManager implements DistributedLockManager {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "lock:";

    @Value("${payment.lock.wait-seconds:5}")
    private int waitSeconds;

    @Value("${payment.lock.lease-seconds:10}")
    private int leaseSeconds;

    @Override
    public <T> T executeWithLock(String key, Supplier<T> action) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);

        try {
            boolean acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Failed to acquire lock: key={}", key);
                throw new LockAcquisitionException("Could not acquire lock for key: " + key);
            }

            log.debug("Lock acquired: key={}", key);
            return action.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted: key={}", key);
            throw new LockAcquisitionException("Lock acquisition was interrupted", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: key={}", key);
            }
        }
    }

    @Override
    public void executeWithLock(String key, Runnable action) {
        executeWithLock(key, () -> {
            action.run();
            return null;
        });
    }
}
