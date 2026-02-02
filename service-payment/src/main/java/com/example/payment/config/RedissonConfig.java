package com.example.payment.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${redisson.address:redis://localhost:6379}")
    private String redisAddress;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress(redisAddress)
            .setConnectionMinimumIdleSize(50)
            .setConnectionPoolSize(200)
            .setSubscriptionConnectionMinimumIdleSize(10)
            .setSubscriptionConnectionPoolSize(50)
            .setTimeout(10000)
            .setRetryAttempts(3)
            .setRetryInterval(1500);
        return Redisson.create(config);
    }
}
