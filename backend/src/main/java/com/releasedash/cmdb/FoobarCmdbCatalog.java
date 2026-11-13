package com.releasedash.cmdb;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Reads CMDB items from Foobar's GitHub repo ({@code foobar.cmdb}).
 */
@Service
public class FoobarCmdbCatalog {

    private final FoobarCmdbProperties foobar;
    private final CmdbGithubClient githubClient;
    private final Object lock = new Object();
    private volatile CmdbItemsResponse cache;

    public FoobarCmdbCatalog(FoobarCmdbProperties foobar, CmdbGithubClient githubClient) {
        this.foobar = foobar;
        this.githubClient = githubClient;
    }

    public CmdbItemsResponse list() {
        CmdbProperties.Github github = foobar.getGithub();
        if (tokenBlank(github)) {
            return empty();
        }
        synchronized (lock) {
            CmdbItemsResponse cached = cache;
            if (cached != null && !expired(cached.getFetchedAt())) {
                return cached;
            }
            CmdbItemsResponse fresh = read(github);
            cache = fresh;
            return fresh;
        }
    }

    private CmdbItemsResponse read(CmdbProperties.Github github) {
        String path = foobar.getIndex() != null && foobar.getIndex().getPath() != null
            ? foobar.getIndex().getPath()
            : "cmdb/cis.yaml";
        CmdbGithubFile file = githubClient.fetchFile(github, path);
        List<CmdbItem> items;
        try {
            items = file.parseItems();
        } catch (IllegalArgumentException e) {
            throw new CmdbGithubException("Unable to parse CMDB index YAML: " + e.getMessage(), e);
        }
        return new CmdbItemsResponse(items, Instant.now(), file.sha(), file.htmlUrl());
    }

    private boolean expired(Instant fetchedAt) {
        if (fetchedAt == null) {
            return true;
        }
        return fetchedAt.plusSeconds(Math.max(foobar.getCacheTtlSeconds(), 0)).isBefore(Instant.now());
    }

    private static boolean tokenBlank(CmdbProperties.Github github) {
        return github == null || github.getToken() == null || github.getToken().isBlank();
    }

    private static CmdbItemsResponse empty() {
        return new CmdbItemsResponse(List.of(), null, null, null);
    }
}
