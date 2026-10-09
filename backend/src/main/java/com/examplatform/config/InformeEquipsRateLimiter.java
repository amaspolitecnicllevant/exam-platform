package com.examplatform.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita els informes d'ordinadors per IP: un ordinador n'envia un cada pocs minuts, així que un ritme molt
 * més alt és un error o un abús (l'endpoint no demana inici de sessió, només el testimoni).
 */
@Component
public class InformeEquipsRateLimiter {

    static final int MAX_PER_HORA = 20;
    private static final int MAX_CLAUS = 20_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void consumeix(String ip) {
        if (buckets.size() > MAX_CLAUS) buckets.clear();
        Bucket b = buckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(MAX_PER_HORA).refillGreedy(MAX_PER_HORA, Duration.ofHours(1)).build())
                .build());
        if (!b.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Massa informes des d'aquesta adreça");
        }
    }
}
