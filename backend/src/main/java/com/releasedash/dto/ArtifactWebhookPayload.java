package com.releasedash.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactWebhookPayload {
    @NotBlank(message = "Repository is required")
    private String repository;
    
    @NotNull(message = "PR number is required")
    private Integer prNumber;
    
    @NotBlank(message = "Environment is required")
    private String environment;
    
    @NotBlank(message = "Commit SHA is required")
    private String commitSha;
    
    @NotBlank(message = "Branch is required")
    private String branch;
    
    private String workflowRunId;
    
    @NotNull(message = "Artifacts are required")
    @Valid
    private ArtifactPaths artifacts;
}
