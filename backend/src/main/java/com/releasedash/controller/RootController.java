package com.releasedash.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {
    
    @GetMapping("/healthcheck")
    public ResponseEntity<Map<String, String>> healthcheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "release-dashboard-backend"
        ));
    }
    
    @GetMapping("/readyz")
    public ResponseEntity<Map<String, String>> readyz() {
        return ResponseEntity.ok(Map.of(
            "status", "ready",
            "service", "release-dashboard-backend"
        ));
    }
    
    @GetMapping("/livez")
    public ResponseEntity<Map<String, String>> livez() {
        return ResponseEntity.ok(Map.of(
            "status", "alive",
            "service", "release-dashboard-backend"
        ));
    }
}
