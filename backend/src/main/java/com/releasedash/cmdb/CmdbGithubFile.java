package com.releasedash.cmdb;

import java.util.List;

public record CmdbGithubFile(String decodedContent, String sha, String htmlUrl) {
    public List<CmdbItem> parseItems() {
        return CmdbIndexParser.parse(decodedContent);
    }
}
