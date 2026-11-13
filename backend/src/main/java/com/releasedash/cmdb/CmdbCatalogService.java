package com.releasedash.cmdb;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CmdbCatalogService {

    private final CmdbProperties properties;
    private final CmdbGithubClient githubClient;
    private final Object lock = new Object();
    private volatile CmdbItemsResponse cache;

    public CmdbCatalogService(CmdbProperties properties, CmdbGithubClient githubClient) {
        this.properties = properties;
        this.githubClient = githubClient;
    }

    public CmdbItemsResponse list(String category, String status) {
        CmdbItemsResponse catalog = getCatalog(false);
        List<CmdbItem> filtered = catalog.getItems().stream()
            .filter(item -> matches(item.getCategory(), category))
            .filter(item -> matches(item.getStatus(), status))
            .collect(Collectors.toList());
        return new CmdbItemsResponse(filtered, catalog.getFetchedAt(), catalog.getSha(), catalog.getHtmlUrl());
    }

    public CmdbItemView getItem(String ciNumber) {
        if (ciNumber == null || ciNumber.isBlank()) {
            throw new CmdbItemNotFoundException("");
        }
        String wanted = ciNumber.trim();
        CmdbItemsResponse catalog = getCatalog(false);
        CmdbItem match = catalog.getItems().stream()
            .filter(item -> wanted.equals(item.getCiNumber()))
            .findFirst()
            .orElseThrow(() -> new CmdbItemNotFoundException(wanted));
        return toView(match);
    }

    public CmdbItemsResponse refresh() {
        return getCatalog(true);
    }

    public String nextNumber(String category) {
        CmdbItemsResponse catalog = getCatalog(false);
        List<String> ids = catalog.getItems().stream()
            .map(CmdbItem::getCiNumber)
            .toList();
        return CmdbNumbers.nextNumber(category, ids);
    }

    private CmdbItemsResponse getCatalog(boolean forceRefresh) {
        if (!forceRefresh) {
            CmdbItemsResponse snapshot = cache;
            if (snapshot != null && !expired(snapshot.getFetchedAt())) {
                return snapshot;
            }
        }
        synchronized (lock) {
            if (!forceRefresh) {
                CmdbItemsResponse snapshot = cache;
                if (snapshot != null && !expired(snapshot.getFetchedAt())) {
                    return snapshot;
                }
            }
            String path = properties.getIndex() != null ? properties.getIndex().getPath() : "cmdb/cis.yaml";
            CmdbGithubFile file = githubClient.fetchFile(path);
            List<CmdbItem> items;
            try {
                items = file.parseItems();
            } catch (IllegalArgumentException e) {
                throw new CmdbGithubException("Unable to parse CMDB index YAML: " + e.getMessage(), e);
            }
            CmdbItemsResponse fresh = new CmdbItemsResponse(
                items,
                Instant.now(),
                file.sha(),
                file.htmlUrl()
            );
            cache = fresh;
            return fresh;
        }
    }

    private boolean expired(Instant fetchedAt) {
        if (fetchedAt == null) {
            return true;
        }
        int ttl = properties.getCache() != null ? properties.getCache().getTtlSeconds() : 45;
        return fetchedAt.plusSeconds(Math.max(ttl, 0)).isBefore(Instant.now());
    }

    private static boolean matches(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return actual.trim().equalsIgnoreCase(expected.trim());
    }

    private CmdbItemView toView(CmdbItem item) {
        CmdbItemView view = new CmdbItemView();
        BeanUtils.copyProperties(item, view);
        try {
            String gitPath = CmdbPaths.filePathFor(item.getCategory(), item.getCiNumber());
            view.setGitPath(gitPath);
            CmdbProperties.Github github = properties.getGithub();
            String owner = github.getOwner();
            String repo = github.getRepo();
            String branch = github.getBranch() == null || github.getBranch().isBlank() ? "main" : github.getBranch();
            if (owner != null && repo != null && !owner.isBlank() && !repo.isBlank()) {
                view.setHtmlUrl(String.format(
                    "https://github.com/%s/%s/blob/%s/%s",
                    owner.trim(),
                    repo.trim(),
                    branch.trim(),
                    gitPath
                ));
            }
        } catch (IllegalArgumentException ignored) {
            // unknown category — still return the index fields
        }
        return view;
    }
}
