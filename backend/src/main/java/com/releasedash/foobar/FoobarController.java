package com.releasedash.foobar;

import com.releasedash.api.TeamApis;
import com.releasedash.cmdb.CmdbItemsResponse;
import com.releasedash.cmdb.FoobarCmdbCatalog;
import com.releasedash.dto.LiveServicesCatalogResponse;
import com.releasedash.dto.ReleaseResponse;
import com.releasedash.dto.StatsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Foobar team API. Paths match Mentat's {@code /mentat/api} surface so the
 * Foobar pages can call the same resources under {@link TeamApis#FOOBAR}.
 * CMDB items are read from {@code foobar.cmdb} ({@code foobarcmdb}).
 * Release artifacts are read from {@code foobar.jfrog.repo-key} and {@code foobar.jfrog.prefix}.
 * Replace the other empty bodies with this team's data sources.
 */
@RestController
@RequestMapping(TeamApis.FOOBAR)
public class FoobarController {

    private final FoobarCmdbCatalog cmdbCatalog;
    private final FoobarReleaseCatalog releaseCatalog;

    public FoobarController(FoobarCmdbCatalog cmdbCatalog, FoobarReleaseCatalog releaseCatalog) {
        this.cmdbCatalog = cmdbCatalog;
        this.releaseCatalog = releaseCatalog;
    }

    @GetMapping
    public Map<String, String> root() {
        return Map.of(
            "team", "foobar",
            "api", TeamApis.FOOBAR
        );
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return releaseCatalog.stats();
    }

    @GetMapping("/releases")
    public List<ReleaseResponse> releases(
            @RequestParam(required = false) String repository,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return releaseCatalog.list(repository, environment, limit, offset);
    }

    @GetMapping("/releases/{repository}")
    public List<ReleaseResponse> releasesByRepository(
            @PathVariable String repository,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return releaseCatalog.list(repository, environment, limit, offset);
    }

    @GetMapping("/releases/{repository}/{prNumber}")
    public ResponseEntity<ReleaseResponse> release(
            @PathVariable String repository,
            @PathVariable Integer prNumber,
            @RequestParam(required = false) String environment) {
        return releaseCatalog.release(repository, prNumber, environment)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/live-services")
    public LiveServicesCatalogResponse liveServices() {
        LiveServicesCatalogResponse catalog = new LiveServicesCatalogResponse();
        catalog.setEnvironments(List.of());
        catalog.setServices(List.of());
        return catalog;
    }

    @GetMapping("/cmdb/items")
    public CmdbItemsResponse cmdbItems() {
        return cmdbCatalog.list();
    }
}
