package com.releasedash.controller;

import com.releasedash.api.TeamApis;
import com.releasedash.dto.LiveServicesCatalogResponse;
import com.releasedash.service.LiveVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(TeamApis.MENTAT)
@RequiredArgsConstructor
public class LiveVersionController {

    private final LiveVersionService liveVersionService;

    /**
     * Catalog of services and per-environment base URLs (edit live-services.yml to update).
     * The frontend fetches each URL's version path from the browser.
     */
    @GetMapping("/live-services")
    public ResponseEntity<LiveServicesCatalogResponse> catalog() {
        return ResponseEntity.ok(liveVersionService.getCatalog());
    }
}
