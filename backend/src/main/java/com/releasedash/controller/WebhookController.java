package com.releasedash.controller;

import com.releasedash.dto.ArtifactWebhookPayload;
import com.releasedash.dto.ReleaseResponse;
import com.releasedash.service.ReleaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class WebhookController {
    
    private final ReleaseService releaseService;
    
    @Value("${webhook.secret:}")
    private String webhookSecret;
    
    @PostMapping("/webhook/artifact")
    public ResponseEntity<ReleaseResponse> receiveArtifactWebhook(
            @Valid @RequestBody ArtifactWebhookPayload payload,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String webhookSecretHeader) {
        
        // Verify webhook secret if configured
        if (webhookSecret != null && !webhookSecret.isEmpty()) {
            if (!webhookSecret.equals(webhookSecretHeader)) {
                return ResponseEntity.status(401).build();
            }
        }
        
        ReleaseResponse response = releaseService.createOrUpdateRelease(payload);
        return ResponseEntity.ok(response);
    }
}
