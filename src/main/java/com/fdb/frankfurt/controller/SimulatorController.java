package com.fdb.frankfurt.controller;

import com.fdb.frankfurt.service.SimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulatorService simulatorService;

    @PostMapping("/start")
    public ResponseEntity<String> start() {
        simulatorService.start();
        return ResponseEntity.ok("Simulator started");
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stop() {
        simulatorService.stop();
        return ResponseEntity.ok("Simulator stopped");
    }

    @PostMapping("/toggle")
    public ResponseEntity<String> toggle() {
        if (simulatorService.isEnabled()) {
            simulatorService.stop();
            return ResponseEntity.ok("Simulator stopped");
        }
        simulatorService.start();
        return ResponseEntity.ok("Simulator started");
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok(simulatorService.isEnabled() ? "RUNNING" : "STOPPED");
    }
}