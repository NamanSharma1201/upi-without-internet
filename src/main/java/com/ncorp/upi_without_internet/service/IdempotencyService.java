package com.ncorp.upi_without_internet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {
    private final Map<String, Instant> seen = new ConcurrentHashMap<>();
    @Value()
}
