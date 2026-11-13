package com.releasedash.cmdb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CmdbItemsResponse {
    private List<CmdbItem> items = new ArrayList<>();

    @JsonProperty("fetched_at")
    private Instant fetchedAt;

    private String sha;

    @JsonProperty("html_url")
    private String htmlUrl;
}
