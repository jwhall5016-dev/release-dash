package com.releasedash.foobar;

import com.releasedash.dto.ReleaseResponse;
import com.releasedash.dto.StatsResponse;
import com.releasedash.service.ReleaseService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Release artifacts for Foobar, read from {@code foobar.jfrog.repo-key}/{@code foobar.jfrog.prefix}.
 * A blank repo key or prefix returns an empty catalog and does not call JFrog.
 */
@Service
public class FoobarReleaseCatalog {

    private final FoobarJfrogProperties properties;
    private final ReleaseService releaseService;

    public FoobarReleaseCatalog(FoobarJfrogProperties properties, ReleaseService releaseService) {
        this.properties = properties;
        this.releaseService = releaseService;
    }

    public StatsResponse stats() {
        if (!properties.isConfigured()) {
            return new StatsResponse(0L, List.of(), List.of());
        }
        return releaseService.getStats(properties.location());
    }

    public List<ReleaseResponse> list(String repository, String environment, int limit, int offset) {
        if (!properties.isConfigured()) {
            return List.of();
        }
        return releaseService.getReleases(properties.location(), repository, environment, limit, offset);
    }

    public Optional<ReleaseResponse> release(String repository, Integer prNumber, String environment) {
        if (!properties.isConfigured()) {
            return Optional.empty();
        }
        try {
            return Optional.of(releaseService.getRelease(properties.location(), repository, prNumber, environment, true));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
