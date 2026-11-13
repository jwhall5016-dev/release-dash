package com.releasedash.cmdb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CmdbItemView extends CmdbItem {
    @JsonProperty("git_path")
    private String gitPath;

    @JsonProperty("html_url")
    private String htmlUrl;
}
