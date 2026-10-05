package com.examplatform.config;

import io.github.bucket4j.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita les execucions de scripts: màxim 10 per usuari per minut.
 * Evita que un alumne saturi el servidor amb execucions massives.
 */
@Component
public class ExecutionRateLimiter {

    private static final int MAX_PER_MINUTE = 10;
    private final ConcurrentHashMap<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(UUID userId) {
        return buckets.computeIfAbsent(userId, this::newBucket).tryConsume(1);
    }

    private Bucket newBucket(UUID ignored) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(MAX_PER_MINUTE)
                        .refillGreedy(MAX_PER_MINUTE, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
