package com.releasedash.cmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CmdbItem {
    private String name;
    private String category;

    @JsonProperty("ci_number")
    private String ciNumber;

    private String parent;
    private String status;
    private String notes;

    @JsonProperty("doc_type")
    private String docType;

    private Integer revision;
}
