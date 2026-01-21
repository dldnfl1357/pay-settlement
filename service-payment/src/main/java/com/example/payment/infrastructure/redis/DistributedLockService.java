package com.example.payment.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "lock:";

    @Value("${payment.lock.wait-seconds:5}")
    private int waitSeconds;

    @Value("${payment.lock.lease-seconds:10}")
    private int leaseSeconds;

    /**
     * 분산 락을 획득하고 작업 실행
     */
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

    /**
     * 분산 락을 획득하고 작업 실행 (반환값 없음)
     */
    public void executeWithLock(String key, Runnable action) {
        executeWithLock(key, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 월렛 락 키 생성
     */
    public static String walletLockKey(Long walletId) {
        return "wallet:" + walletId;
    }

    /**
     * 결제 락 키 생성
     */
    public static String paymentLockKey(Long paymentId) {
        return "payment:" + paymentId;
    }
}
