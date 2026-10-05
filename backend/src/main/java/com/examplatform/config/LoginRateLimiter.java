package com.examplatform.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita els intents de contrasenya fallits (login i convits) per compte i per IP, per evitar
 * l'endevinament de contrasenyes. Només compten els intents fallits: una classe sencera entrant
 * alhora des de la mateixa xarxa no es bloqueja.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FALLADES_PER_COMPTE = 5;
    static final int MAX_FALLADES_PER_IP = 30;
    private static final Duration FINESTRA = Duration.ofMinutes(15);
    private static final int MAX_CLAUS = 50_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Llança 429 si el compte o la IP han esgotat els intents. No consumeix cap intent. */
    public void assertPermes(String ip, String email) {
        if (bucket("ip:" + ip, MAX_FALLADES_PER_IP).getAvailableTokens() <= 0
                || bucket(compte(email), MAX_FALLADES_PER_COMPTE).getAvailableTokens() <= 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Massa intents fallits. Torna-ho a provar d'aquí a uns minuts.");
        }
    }

    public void registraFallada(String ip, String email) {
        bucket("ip:" + ip, MAX_FALLADES_PER_IP).tryConsume(1);
        bucket(compte(email), MAX_FALLADES_PER_COMPTE).tryConsume(1);
    }

    private static String compte(String email) {
        return "compte:" + (email == null ? "" : email.strip().toLowerCase(Locale.ROOT));
    }

    private Bucket bucket(String key, int capacitat) {
        if (buckets.size() > MAX_CLAUS) buckets.clear();   // evita créixer sense límit amb claus inventades
        return buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacitat).refillGreedy(capacitat, FINESTRA).build())
                .build());
    }
}
