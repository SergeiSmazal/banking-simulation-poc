package com.fdb.frankfurt.controller;

import com.fdb.frankfurt.dto.AnomalyAlert;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class AlertBroadcaster {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedQueue<String> alertQueue = new ConcurrentLinkedQueue<>();

    public AlertBroadcaster(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public String pollAlert() {
        String alert = alertQueue.poll();
        return alert != null ? alert : "{}";
    }

    @Scheduled(fixedRate = 1000)
    public void pollAlerts() {
        try {
            // Read from Redis Pub/Sub (using a list as a simple queue)
            List<String> alerts = new ArrayList<>();
            String alert;
            while ((alert = stringRedisTemplate.opsForList().leftPop("alerts:queue")) != null) {
                alerts.add(alert);
            }
            if (!alerts.isEmpty()) {
                alerts.forEach(alertQueue::add);
            }
        } catch (Exception e) {
            // Ignore - Redis might not be available
        }
    }
}