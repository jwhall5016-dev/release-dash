package com.releasedash.cmdb;

import java.util.Locale;
import java.util.Map;

/**
 * Maps a CI category to the per-CI YAML path in the CMDB Git repo.
 * Does not write {@code cmdb/cis.yaml} (generated index).
 */
public final class CmdbPaths {

    private static final Map<String, String> DIRECTORY_BY_CATEGORY = Map.of(
        "SYS", "systems",
        "ENV", "environments",
        "DOC", "docs",
        "REL", "releases",
        "DMO", "demos",
        "SSC", "software"
    );

    private CmdbPaths() {
    }

    public static String directoryFor(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        String dir = DIRECTORY_BY_CATEGORY.get(category.trim().toUpperCase(Locale.ROOT));
        if (dir == null) {
            throw new IllegalArgumentException("Unknown CMDB category: " + category);
        }
        return dir;
    }

    /**
     * Relative path from the repo root, e.g. {@code systems/MENTAT-SYS-02.yaml}.
     */
    public static String filePathFor(String category, String ciNumber) {
        if (ciNumber == null || ciNumber.isBlank()) {
            throw new IllegalArgumentException("ci_number is required");
        }
        String trimmed = ciNumber.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            throw new IllegalArgumentException("ci_number is not a valid filename: " + ciNumber);
        }
        return directoryFor(category) + "/" + trimmed + ".yaml";
    }
}
