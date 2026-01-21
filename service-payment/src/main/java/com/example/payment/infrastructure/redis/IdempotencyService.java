package com.example.payment.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PREFIX = "idempotency:";
    private static final String PROCESSING = "PROCESSING";

    @Value("${payment.idempotency.ttl-hours:24}")
    private int ttlHours;

    /**
     * 멱등성 키로 캐시된 응답을 조회
     */
    public <T> Optional<T> getResponse(String key, Class<T> responseType) {
        String cached = redisTemplate.opsForValue().get(PREFIX + key);
        if (cached == null || PROCESSING.equals(cached)) {
            return Optional.empty();
        }

        try {
            T response = objectMapper.readValue(cached, responseType);
            log.debug("Idempotency cache hit: key={}", key);
            return Optional.of(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached response: key={}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 멱등성 키 처리 시작 (락 획득)
     * @return true if successfully acquired, false if already exists
     */
    public boolean tryAcquire(String key) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(
            PREFIX + key,
            PROCESSING,
            Duration.ofHours(ttlHours)
        );
        boolean acquired = Boolean.TRUE.equals(result);
        log.debug("Idempotency key acquire attempt: key={}, acquired={}", key, acquired);
        return acquired;
    }

    /**
     * 멱등성 키가 이미 존재하는지 확인
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + key));
    }

    /**
     * 멱등성 키가 처리 중인지 확인
     */
    public boolean isProcessing(String key) {
        String value = redisTemplate.opsForValue().get(PREFIX + key);
        return PROCESSING.equals(value);
    }

    /**
     * 처리 완료 후 응답 저장
     */
    public <T> void complete(String key, T response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(
                PREFIX + key,
                json,
                Duration.ofHours(ttlHours)
            );
            log.debug("Idempotency response cached: key={}", key);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response: key={}", key, e);
            throw new RuntimeException("Failed to cache idempotency response", e);
        }
    }

    /**
     * 처리 실패 시 키 삭제 (재시도 허용)
     */
    public void release(String key) {
        redisTemplate.delete(PREFIX + key);
        log.debug("Idempotency key released: key={}", key);
    }
}
