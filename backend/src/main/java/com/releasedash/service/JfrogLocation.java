package com.releasedash.service;

/**
 * Artifactory folder for one team's release artifacts.
 * Files live at {@code {repoKey}/{prefix}/{repository}/{environment}/{release}/}.
 */
public record JfrogLocation(String repoKey, String prefix) {

    public String storageRoot() {
        return strip(repoKey) + "/" + strip(prefix);
    }

    public static String strip(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
