package com.fdb.frankfurt.controller;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AlertBroadcaster {

    private final CopyOnWriteArrayList<String> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean active = new AtomicBoolean(false);

    public void subscribe(String subscriberId) {
        subscribers.add(subscriberId);
        active.set(true);
    }

    public void unsubscribe(String subscriberId) {
        subscribers.remove(subscriberId);
        if (subscribers.isEmpty()) {
            active.set(false);
        }
    }

    public void broadcast(String message) {
        subscribers.forEach(id -> {
            // In production, this would push to a real WebSocket session
        });
    }

    @Scheduled(fixedRate = 1000)
    public void pollAlerts() {
        // Simplified polling - in production, use a proper Redis subscription
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    public boolean isActive() {
        return active.get();
    }
}