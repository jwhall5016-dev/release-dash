package com.releasedash.controller;

import com.releasedash.api.TeamApis;
import com.releasedash.dto.ReleaseResponse;
import com.releasedash.dto.StatsResponse;
import com.releasedash.service.JfrogService;
import com.releasedash.service.ReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(TeamApis.MENTAT)
@RequiredArgsConstructor
public class ReleaseController {
    
    private final ReleaseService releaseService;
    private final JfrogService jfrogService;
    
    @GetMapping("/releases")
    public ResponseEntity<List<ReleaseResponse>> listReleases(
            @RequestParam(required = false) String repository,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        List<ReleaseResponse> releases = releaseService.getReleases(repository, environment, limit, offset);
        return ResponseEntity.ok(releases);
    }
    
    @GetMapping("/releases/{repository}")
    public ResponseEntity<List<ReleaseResponse>> getReleasesByRepository(
            @PathVariable String repository,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        List<ReleaseResponse> releases = releaseService.getReleases(repository, environment, limit, offset);
        return ResponseEntity.ok(releases);
    }
    
    @GetMapping("/releases/{repository}/{prNumber}")
    public ResponseEntity<ReleaseResponse> getRelease(
            @PathVariable String repository,
            @PathVariable Integer prNumber,
            @RequestParam(required = false) String environment) {
        
        ReleaseResponse release = releaseService.getRelease(repository, prNumber, environment);
        return ResponseEntity.ok(release);
    }

    /**
     * Correlate a live build hash with a JFrog release for the given repository.
     */
    @GetMapping("/build-hash-match")
    public ResponseEntity<?> matchReleaseByBuildHash(
            @RequestParam String repository,
            @RequestParam String buildHash,
            @RequestParam(required = false) String environment) {
        ReleaseResponse match = releaseService.findByBuildHash(repository, buildHash, environment);
        if (match == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(java.util.Map.of(
                    "error", "No release found for repository=" + repository + " buildHash=" + buildHash
                ));
        }
        return ResponseEntity.ok(match);
    }
    
    @GetMapping("/artifacts/{repository}/{prNumber}/download/{filename}")
    public ResponseEntity<?> downloadArtifact(
            @PathVariable String repository,
            @PathVariable Integer prNumber,
            @PathVariable String filename,
            @RequestParam(required = false) String environment) {
        
        try {
            // Get release to determine environment if not provided (skip commit enrichment)
            ReleaseResponse release = releaseService.getRelease(repository, prNumber, environment, false);
            String env = environment != null ? environment : release.getEnvironment();
            
            // Try to find the actual artifact path from the release's artifacts map
            // This handles cases where the stored path might be different from the filename
            String actualFilename = filename;
            if (release.getArtifacts() != null) {
                // Map filename to artifact key
                String artifactKey = null;
                if (filename.contains("cyclonedx") || filename.endsWith(".cdx.json")) {
                    artifactKey = "sbom_cyclonedx";
                } else if (filename.contains("spdx") || filename.endsWith(".spdx.json")) {
                    artifactKey = "sbom_spdx";
                } else if (filename.contains("summary")) {
                    artifactKey = "sbom_summary";
                } else if (filename.contains("release-notes") || filename.contains("RELEASE_NOTES")) {
                    artifactKey = "release_notes";
                } else if (filename.contains("trivy") && filename.endsWith(".html")) {
                    artifactKey = "trivy_report";
                }
                
                // If we found a matching artifact, extract just the filename from the stored path
                if (artifactKey != null && release.getArtifacts().containsKey(artifactKey)) {
                    String artifactPath = release.getArtifacts().get(artifactKey);
                    if (artifactPath != null && !artifactPath.isEmpty()) {
                        // Extract filename from path (handle both simple filename and full path)
                        if (artifactPath.contains("/")) {
                            actualFilename = artifactPath.substring(artifactPath.lastIndexOf("/") + 1);
                        } else {
                            actualFilename = artifactPath;
                        }
                        System.out.println("Using artifact path: " + artifactPath + ", extracted filename: " + actualFilename);
                    }
                }
            }
            
            // Use the release directory if available (for version-based releases)
            String releaseDir = release.getReleaseDirectory();
            byte[] artifactData = jfrogService.downloadArtifact(repository, env, prNumber, releaseDir, actualFilename);
            
            if (artifactData == null || artifactData.length == 0) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("error", "Artifact file not found or empty: " + filename));
            }
            
            ByteArrayResource resource = new ByteArrayResource(artifactData);
            
            // Set appropriate content type based on file extension
            MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
            if (filename.endsWith(".html")) {
                contentType = MediaType.TEXT_HTML;
            } else if (filename.endsWith(".json")) {
                contentType = MediaType.APPLICATION_JSON;
            } else if (filename.endsWith(".md") || filename.endsWith(".txt")) {
                contentType = MediaType.TEXT_PLAIN;
            }
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .body(resource);
        } catch (RuntimeException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(java.util.Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/artifacts/{repository}/{prNumber}/view/{filename}")
    public ResponseEntity<?> viewArtifact(
            @PathVariable String repository,
            @PathVariable Integer prNumber,
            @PathVariable String filename,
            @RequestParam(required = false) String environment) {
        
        try {
            // Get release to determine environment if not provided (skip commit enrichment)
            ReleaseResponse release = releaseService.getRelease(repository, prNumber, environment, false);
            String env = environment != null ? environment : release.getEnvironment();
            
            // Try to find the actual artifact path from the release's artifacts map
            String actualFilename = filename;
            if (release.getArtifacts() != null) {
                String artifactKey = null;
                if (filename.contains("cyclonedx") || filename.endsWith(".cdx.json")) {
                    artifactKey = "sbom_cyclonedx";
                } else if (filename.contains("spdx") || filename.endsWith(".spdx.json")) {
                    artifactKey = "sbom_spdx";
                } else if (filename.contains("summary")) {
                    artifactKey = "sbom_summary";
                } else if (filename.contains("release-notes") || filename.contains("RELEASE_NOTES")) {
                    artifactKey = "release_notes";
                } else if (filename.contains("trivy") && filename.endsWith(".html")) {
                    artifactKey = "trivy_report";
                }
                
                if (artifactKey != null && release.getArtifacts().containsKey(artifactKey)) {
                    String artifactPath = release.getArtifacts().get(artifactKey);
                    if (artifactPath != null && !artifactPath.isEmpty()) {
                        if (artifactPath.contains("/")) {
                            actualFilename = artifactPath.substring(artifactPath.lastIndexOf("/") + 1);
                        } else {
                            actualFilename = artifactPath;
                        }
                    }
                }
            }
            
            // Use the release directory if available (for version-based releases)
            String releaseDir = release.getReleaseDirectory();
            byte[] artifactData = jfrogService.downloadArtifact(repository, env, prNumber, releaseDir, actualFilename);
            
            if (artifactData == null || artifactData.length == 0) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("error", "Artifact file not found or empty: " + filename));
            }
            
            // Return content as text/string for inline display (no download header)
            String content = new String(artifactData, java.nio.charset.StandardCharsets.UTF_8);
            
            MediaType contentType = MediaType.TEXT_PLAIN;
            if (filename.endsWith(".html")) {
                contentType = MediaType.TEXT_HTML;
            } else if (filename.endsWith(".md")) {
                contentType = MediaType.TEXT_PLAIN; // Will be rendered as markdown on frontend
            } else if (filename.endsWith(".json")) {
                contentType = MediaType.APPLICATION_JSON;
            }
            
            return ResponseEntity.ok()
                .contentType(contentType)
                .body(content);
        } catch (RuntimeException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(java.util.Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        StatsResponse stats = releaseService.getStats();
        return ResponseEntity.ok(stats);
    }
}
