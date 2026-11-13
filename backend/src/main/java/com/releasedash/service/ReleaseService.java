package com.releasedash.service;

import com.releasedash.dto.ArtifactWebhookPayload;
import com.releasedash.dto.ReleaseResponse;
import com.releasedash.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReleaseService {
    
    private final JfrogService jfrogService;
    
    /**
     * Webhook handler - just validates and returns the payload as a response
     * Since we're querying JFrog directly, we don't need to store webhook data
     */
    public ReleaseResponse createOrUpdateRelease(ArtifactWebhookPayload payload) {
        ReleaseResponse response = new ReleaseResponse();
        response.setRepository(payload.getRepository());
        response.setPrNumber(payload.getPrNumber());
        response.setEnvironment(payload.getEnvironment());
        response.setCommitSha(payload.getCommitSha());
        response.setBranch(payload.getBranch());
        response.setWorkflowRunId(payload.getWorkflowRunId());
        
        Map<String, String> artifacts = Map.of(
            "sbom_cyclonedx", payload.getArtifacts().getSbomCyclonedx(),
            "sbom_spdx", payload.getArtifacts().getSbomSpdx(),
            "sbom_summary", payload.getArtifacts().getSbomSummary(),
            "release_notes", payload.getArtifacts().getReleaseNotes()
        );
        response.setArtifacts(artifacts);
        
        // Generate a synthetic ID based on repository, PR, and environment
        response.setId(generateId(payload.getRepository(), payload.getPrNumber(), payload.getEnvironment()));
        response.setCreatedAt(java.time.LocalDateTime.now());
        response.setUpdatedAt(java.time.LocalDateTime.now());
        
        return response;
    }
    
    public List<ReleaseResponse> getReleases(String repository, String environment, int limit, int offset) {
        return getReleases(jfrogService.mentatLocation(), repository, environment, limit, offset);
    }

    public List<ReleaseResponse> getReleases(JfrogLocation location, String repository, String environment, int limit, int offset) {
        List<JfrogService.ReleaseInfo> releases = jfrogService.listReleases(location, repository, environment);
        
        List<ReleaseResponse> responses = releases.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
        
        // Apply pagination
        int end = Math.min(offset + limit, responses.size());
        return responses.subList(Math.min(offset, responses.size()), end);
    }
    
    public ReleaseResponse getRelease(String repository, Integer prNumber, String environment) {
        return getRelease(repository, prNumber, environment, true);
    }

    public ReleaseResponse getRelease(String repository, Integer prNumber, String environment, boolean enrichMetadata) {
        return getRelease(jfrogService.mentatLocation(), repository, prNumber, environment, enrichMetadata);
    }

    public ReleaseResponse getRelease(JfrogLocation location, String repository, Integer prNumber, String environment, boolean enrichMetadata) {
        List<JfrogService.ReleaseInfo> releases = jfrogService.listReleases(location, repository, environment);
        
        Optional<JfrogService.ReleaseInfo> release = releases.stream()
            .filter(r -> r.getRepository().equals(repository) && r.getPrNumber().equals(prNumber))
            .findFirst();
        
        if (release.isEmpty()) {
            throw new RuntimeException("Release not found");
        }

        JfrogService.ReleaseInfo info = release.get();
        if (enrichMetadata) {
            jfrogService.enrichCommitMetadata(location, info, false);
        }
        
        return toResponse(info);
    }
    
    public StatsResponse getStats() {
        return getStats(jfrogService.mentatLocation());
    }

    public StatsResponse getStats(JfrogLocation location) {
        List<JfrogService.ReleaseInfo> releases = jfrogService.listReleases(location, null, null);
        
        Set<String> repositories = releases.stream()
            .map(JfrogService.ReleaseInfo::getRepository)
            .collect(Collectors.toSet());
        
        Set<String> environments = releases.stream()
            .map(JfrogService.ReleaseInfo::getEnvironment)
            .collect(Collectors.toSet());
        
        return new StatsResponse((long) releases.size(), new ArrayList<>(repositories), new ArrayList<>(environments));
    }

    /**
     * Find a release whose commit SHA matches {@code buildHash} (prefix-aware).
     * Enriches commit metadata from release notes only for speed.
     */
    public ReleaseResponse findByBuildHash(String repository, String buildHash, String preferredEnvironment) {
        if (repository == null || buildHash == null || buildHash.isBlank()) {
            return null;
        }

        String primaryEnv = preferredEnvironment;
        if ("production-gov".equalsIgnoreCase(preferredEnvironment)) {
            primaryEnv = "production";
        }

        List<String> envsToSearch = new ArrayList<>();
        if (primaryEnv != null && !primaryEnv.isBlank()) {
            envsToSearch.add(primaryEnv);
            String other = "production".equals(primaryEnv) ? "staging" : "production";
            envsToSearch.add(other);
        } else {
            envsToSearch.add("staging");
            envsToSearch.add("production");
        }

        for (String env : envsToSearch) {
            List<JfrogService.ReleaseInfo> releases = jfrogService.listReleases(repository, env);
            // Newest first — listReleases sorts by prNumber desc which is synthetic for versions;
            // prefer createdAt desc for matching.
            releases.sort((a, b) -> {
                if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                if (a.getCreatedAt() == null) return 1;
                if (b.getCreatedAt() == null) return -1;
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            });
            for (JfrogService.ReleaseInfo info : releases) {
                if (!repository.equals(info.getRepository())) {
                    continue;
                }
                jfrogService.enrichCommitMetadata(info, true);
                if (JfrogService.hashesMatch(info.getCommitSha(), buildHash)) {
                    return toResponse(info);
                }
            }
        }
        return null;
    }
    
    private ReleaseResponse toResponse(JfrogService.ReleaseInfo release) {
        ReleaseResponse response = new ReleaseResponse();
        response.setId(generateId(release.getRepository(), release.getPrNumber(), release.getEnvironment()));
        response.setRepository(release.getRepository());
        response.setPrNumber(release.getPrNumber());
        response.setEnvironment(release.getEnvironment());
        response.setCommitSha(release.getCommitSha());
        response.setBranch(release.getBranch());
        response.setWorkflowRunId(release.getWorkflowRunId());
        response.setArtifacts(release.getArtifacts());
        response.setCreatedAt(release.getCreatedAt());
        response.setUpdatedAt(release.getUpdatedAt());
        response.setReleaseDirectory(release.getReleaseDirectory());
        // Use the release tag from JFrog if available, otherwise show PR# as version
        if (release.getReleaseTag() != null && !release.getReleaseTag().isEmpty()) {
            response.setReleaseTag(release.getReleaseTag());
        } else {
            // If directory is version-based (starts with "v" or matches version pattern), use it
            if (release.getReleaseDirectory() != null && 
                (release.getReleaseDirectory().startsWith("v") || release.getReleaseDirectory().matches("^[0-9]+\\.[0-9]+"))) {
                response.setReleaseTag(release.getReleaseDirectory());
            } else {
                // No version available, show PR# as the version
                response.setReleaseTag("PR-" + release.getPrNumber());
            }
        }
        return response;
    }
    
    private Long generateId(String repository, Integer prNumber, String environment) {
        // Generate a deterministic ID from repository, PR, and environment
        String idString = repository + "-" + prNumber + "-" + environment;
        return (long) idString.hashCode();
    }
}
