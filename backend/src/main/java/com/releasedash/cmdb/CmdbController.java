package com.releasedash.cmdb;

import com.releasedash.api.TeamApis;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CMDB HTTP surface. Catalog reads GitHub when {@code cmdb.enabled} is true.
 * Creates open a GitHub PR (default). Edits stay 501. {@code GET /schema} always returns the bundled snapshot.
 */
@RestController
@RequestMapping(TeamApis.MENTAT + "/cmdb")
@RequiredArgsConstructor
public class CmdbController {

    private final CmdbProperties properties;
    private final CmdbSchemaService schemaService;
    private final CmdbCatalogService catalogService;
    private final CmdbWriteService writeService;

    @GetMapping("/items")
    public ResponseEntity<CmdbItemsResponse> listItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        requireEnabled();
        return ResponseEntity.ok(catalogService.list(category, status));
    }

    @GetMapping("/items/{ciNumber}")
    public ResponseEntity<CmdbItemView> getItem(@PathVariable String ciNumber) {
        requireEnabled();
        return ResponseEntity.ok(catalogService.getItem(ciNumber));
    }

    @GetMapping("/schema")
    public ResponseEntity<?> schema() {
        return ResponseEntity.ok(schemaService.snapshot());
    }

    @GetMapping("/next-number")
    public ResponseEntity<Map<String, String>> nextNumber(@RequestParam String category) {
        requireEnabled();
        return ResponseEntity.ok(Map.of("ci_number", catalogService.nextNumber(category)));
    }

    @PostMapping("/items")
    public ResponseEntity<CmdbWriteResult> createItem(@RequestBody CmdbItem item) {
        requireEnabled();
        return ResponseEntity.status(201).body(writeService.create(item));
    }

    @PutMapping("/items/{ciNumber}")
    public ResponseEntity<?> updateItem(
            @PathVariable String ciNumber,
            @RequestParam(required = false) String baseSha,
            @RequestBody CmdbItem item) {
        requireEnabled();
        throw new CmdbNotImplementedException();
    }

    @PostMapping("/refresh")
    public ResponseEntity<CmdbItemsResponse> refresh() {
        requireEnabled();
        return ResponseEntity.ok(catalogService.refresh());
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new CmdbNotEnabledException();
        }
    }
}
