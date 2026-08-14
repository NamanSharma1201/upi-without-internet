package com.ncorp.upi_without_internet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {
    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    @Value("${upi.mesh.idempotency-ttl-seconds:86400}")
    private long ttlSeconds;

    public boolean claim(String packetHash){
        Instant now = Instant.now();
        Instant prev = seen.putIfAbsent(packetHash, now);
        return  prev == null;
    }

    public int size(){
        return seen.size();
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpired(){
        Instant cutoff = Instant.now().minusSeconds(ttlSeconds);
        seen.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    public void clear(){
        seen.clear();
    }
}
