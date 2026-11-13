package com.releasedash.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactPaths {
    @JsonProperty("sbom_cyclonedx")
    private String sbomCyclonedx;
    
    @JsonProperty("sbom_spdx")
    private String sbomSpdx;
    
    @JsonProperty("sbom_summary")
    private String sbomSummary;
    
    @JsonProperty("release_notes")
    private String releaseNotes;
}
