package com.example.payment.infrastructure.redis;

import com.example.payment.application.port.IdempotencyStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PREFIX = "idempotency:";
    private static final String PROCESSING = "PROCESSING";

    @Value("${payment.idempotency.ttl-hours:24}")
    private int ttlHours;

    @Override
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

    @Override
    public boolean tryAcquire(String key) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(
            PREFIX + key, PROCESSING, Duration.ofHours(ttlHours));
        boolean acquired = Boolean.TRUE.equals(result);
        log.debug("Idempotency key acquire attempt: key={}, acquired={}", key, acquired);
        return acquired;
    }

    @Override
    public <T> void complete(String key, T response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(PREFIX + key, json, Duration.ofHours(ttlHours));
            log.debug("Idempotency response cached: key={}", key);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response: key={}", key, e);
            throw new RuntimeException("Failed to cache idempotency response", e);
        }
    }

    @Override
    public void release(String key) {
        redisTemplate.delete(PREFIX + key);
        log.debug("Idempotency key released: key={}", key);
    }
}
