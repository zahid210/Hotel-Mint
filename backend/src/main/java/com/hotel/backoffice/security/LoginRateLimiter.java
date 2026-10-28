package com.hotel.backoffice.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

@Component
public class LoginRateLimiter {

    private static final int MAX_INTENTOS_EMAIL = 5;
    private static final int MAX_INTENTOS_IP    = 20;
    private static final Duration VENTANA       = Duration.ofMinutes(15);

    // Bounded caches with automatic eviction after 15 minutes to prevent memory leaks
    private final Cache<String, Deque<Long>> intentosEmail = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(5000)
            .build();

    private final Cache<String, Deque<Long>> intentosIp = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(5000)
            .build();

    public boolean bloqueado(String email) {
        return estaBloqueado(intentosEmail, normalizar(email), MAX_INTENTOS_EMAIL);
    }

    public boolean bloqueado(String email, String ip) {
        if (estaBloqueado(intentosEmail, normalizar(email), MAX_INTENTOS_EMAIL)) {
            return true;
        }
        if (ip != null && !ip.isBlank() && estaBloqueado(intentosIp, ip.trim(), MAX_INTENTOS_IP)) {
            return true;
        }
        return false;
    }

    public void registrarFallo(String email) {
        registrarEnCache(intentosEmail, normalizar(email), MAX_INTENTOS_EMAIL);
    }

    public void registrarFallo(String email, String ip) {
        registrarFallo(email);
        if (ip != null && !ip.isBlank()) {
            registrarEnCache(intentosIp, ip.trim(), MAX_INTENTOS_IP);
        }
    }

    public void limpiar(String email) {
        if (email != null) {
            intentosEmail.invalidate(normalizar(email));
        }
    }

    public void limpiar(String email, String ip) {
        limpiar(email);
        // Note: we don't necessarily clear IP on a single success to avoid allowing brute-force
        // rotation across passwords, but we can decrement or keep IP clean on successful auth.
    }

    private boolean estaBloqueado(Cache<String, Deque<Long>> cache, String key, int maxIntentos) {
        if (key == null || key.isBlank()) return false;
        Deque<Long> cola = cache.getIfPresent(key);
        if (cola == null) return false;

        long ahora = System.currentTimeMillis();
        while (!cola.isEmpty() && ahora - cola.peekFirst() > VENTANA.toMillis()) {
            cola.pollFirst();
        }
        if (cola.isEmpty()) {
            cache.invalidate(key);
            return false;
        }
        return cola.size() >= maxIntentos;
    }

    private void registrarEnCache(Cache<String, Deque<Long>> cache, String key, int maxIntentos) {
        if (key == null || key.isBlank()) return;
        Deque<Long> cola = cache.get(key, k -> new ConcurrentLinkedDeque<>());
        if (cola != null) {
            cola.addLast(System.currentTimeMillis());
            while (cola.size() > maxIntentos) {
                cola.pollFirst();
            }
        }
    }

    private String normalizar(String email) {
        return email == null ? "" : email.toLowerCase().trim();
    }
}
