package com.fdb.frankfurt.controller;

import com.fdb.frankfurt.dto.AnomalyAlert;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.function.Supplier;

@RestController
public class AlertWebSocketController {

    private final AlertBroadcaster broadcaster;

    public AlertWebSocketController(AlertBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(value = "/alerts/stream", produces = "text/event-stream")
    public Flux<ServerSentEvent<String>> streamAlerts() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(seq -> {
                    String alertJson = broadcaster.pollAlert();
                    return ServerSentEvent.<String>builder()
                            .id(String.valueOf(seq))
                            .event("anomaly")
                            .data(alertJson)
                            .build();
                });
    }
}