package com.releasedash.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseResponse {
    private Long id;
    private String repository;
    
    @JsonProperty("pr_number")
    private Integer prNumber;
    
    private String environment;
    
    @JsonProperty("commit_sha")
    private String commitSha;
    
    private String branch;
    
    @JsonProperty("workflow_run_id")
    private String workflowRunId;
    
    @JsonProperty("release_tag")
    private String releaseTag;
    
    @JsonProperty("release_directory")
    private String releaseDirectory; // The actual directory name in JFrog (e.g., "pr-123", "v1.0.0", "1.0.0")
    
    private Map<String, String> artifacts;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
