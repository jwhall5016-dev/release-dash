package com.releasedash.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasedash.config.AppConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class JfrogService {

    private final AppConfig appConfig;
    private final WebClient.Builder webClientBuilder;
    private final JfrogLocation mentatLocation;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JfrogService(
            AppConfig appConfig,
            WebClient.Builder webClientBuilder,
            @Value("${mentat.jfrog.repo-key:ssa-generic}") String repoKey,
            @Value("${mentat.jfrog.prefix:generic-release-artifacts}") String prefix) {
        this.appConfig = appConfig;
        this.webClientBuilder = webClientBuilder;
        this.mentatLocation = new JfrogLocation(repoKey, prefix);
    }

    public JfrogLocation mentatLocation() {
        return mentatLocation;
    }
    
    private WebClient getWebClient() {
        // Configure HttpClient to follow redirects (JFrog returns 302 redirects for file downloads)
        HttpClient httpClient = HttpClient.create()
            .followRedirect(true);
        
        return webClientBuilder.clone()
            .baseUrl(appConfig.getJfrogUrl())
            .defaultHeaders(headers -> {
                headers.setBasicAuth(appConfig.getJfrogUsername(), appConfig.getJfrogToken());
            })
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
    
    public byte[] downloadArtifact(String repository, String environment, Integer prNumber, String filename) {
        return downloadArtifact(mentatLocation, repository, environment, prNumber, null, filename);
    }

    public byte[] downloadArtifact(String repository, String environment, Integer prNumber, String releaseDirectory, String filename) {
        return downloadArtifact(mentatLocation, repository, environment, prNumber, releaseDirectory, filename);
    }

    public byte[] downloadArtifact(JfrogLocation location, String repository, String environment, Integer prNumber, String releaseDirectory, String filename) {
        // For version-based releases (staging/production), use the actual directory name instead of pr-{number}
        String repoKey = JfrogLocation.strip(location.repoKey());
        String prefix = JfrogLocation.strip(location.prefix());
        String releaseDir;
        if (releaseDirectory != null && !releaseDirectory.isEmpty()) {
            // Use the provided directory name
            releaseDir = releaseDirectory;
        } else {
            // Fall back to pr-{number} format for PR-based releases
            releaseDir = String.format("pr-%d", prNumber);
        }
        String basePath = String.format("%s/%s/%s/%s", prefix, repository, environment, releaseDir);
        
        // Construct the URI - handle both cases:
        // 1. If filename is a full path (from artifact map), use it directly
        // 2. If filename is just a name, construct the path
        String uri;
        if (filename.contains("/")) {
            // Filename is actually a full path, check if it starts with repo key
            if (filename.startsWith(repoKey + "/")) {
                uri = "/" + filename; // Already has repo key
            } else if (filename.startsWith(prefix + "/")) {
                uri = "/" + repoKey + "/" + filename; // Add repo key
            } else {
                uri = "/" + repoKey + "/" + basePath + "/" + filename.substring(filename.lastIndexOf("/") + 1);
            }
        } else {
            // Just a filename, construct full path
            uri = String.format("/%s/%s/%s", repoKey, basePath, filename);
        }
        
        System.out.println("=== Downloading artifact ===");
        System.out.println("Repository: " + repository);
        System.out.println("Environment: " + environment);
        System.out.println("PR Number: " + prNumber);
        System.out.println("Filename parameter: " + filename);
        System.out.println("Constructed URI: " + uri);
        System.out.println("Base URL: " + appConfig.getJfrogUrl());
        System.out.println("Full download URL: " + appConfig.getJfrogUrl() + uri);
        
        try {
            // Use exchangeToMono to get raw response body regardless of content type
            // This ensures JSON files are downloaded as raw bytes, not parsed as JSON
            Flux<DataBuffer> dataBufferFlux = getWebClient().get()
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "*/*") // Accept any content type
                .exchangeToFlux(clientResponse -> {
                    System.out.println("HTTP Status Code: " + clientResponse.statusCode());
                    // WebClient with followRedirect(true) will automatically follow 3xx redirects
                    // So we only need to check for actual errors (4xx, 5xx)
                    if (clientResponse.statusCode().is4xxClientError() || clientResponse.statusCode().is5xxServerError()) {
                        System.err.println("HTTP Error: " + clientResponse.statusCode() + " for URI: " + uri);
                        System.err.println("Full URL: " + appConfig.getJfrogUrl() + uri);
                        return clientResponse.bodyToMono(String.class)
                            .doOnNext(body -> {
                                System.err.println("Error response body (first 500 chars): " + 
                                    (body != null && body.length() > 500 ? body.substring(0, 500) : body));
                            })
                            .flatMapMany(body -> Mono.error(new RuntimeException("Failed to download artifact: HTTP " + clientResponse.statusCode())));
                    }
                    // 2xx (success) or 3xx (redirect - will be followed automatically)
                    System.out.println("Successfully received response (status " + clientResponse.statusCode() + "), reading body...");
                    return clientResponse.bodyToFlux(DataBuffer.class);
                });
            
            // Convert DataBuffer Flux to byte array
            byte[] data = DataBufferUtils.join(dataBufferFlux)
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    System.out.println("Read " + bytes.length + " bytes from response");
                    return bytes;
                })
                .block();
            
            if (data == null || data.length == 0) {
                System.err.println("ERROR: Downloaded data is null or empty");
                throw new RuntimeException("Artifact file not found or empty: " + filename);
            }
            
            System.out.println("Successfully downloaded " + data.length + " bytes");
            return data;
        } catch (Exception e) {
            System.err.println("Error downloading artifact: " + e.getMessage());
            System.err.println("Exception type: " + e.getClass().getName());
            e.printStackTrace();
            throw new RuntimeException("Failed to download artifact: " + filename + " - " + e.getMessage(), e);
        }
    }
    
    /**
     * List all releases by querying JFrog Artifactory directory structure
     */
    public List<ReleaseInfo> listReleases(String repositoryFilter, String environmentFilter) {
        return listReleases(mentatLocation, repositoryFilter, environmentFilter);
    }

    public List<ReleaseInfo> listReleases(JfrogLocation location, String repositoryFilter, String environmentFilter) {
        List<ReleaseInfo> releases = new ArrayList<>();
        
        System.out.println("=== Starting listReleases ===");
        System.out.println("Repository filter: " + repositoryFilter);
        System.out.println("Environment filter: " + environmentFilter);
        System.out.println("Artifact folder: " + location.storageRoot());
        
        try {
            String basePath = location.storageRoot();
            System.out.println("Querying base path: " + basePath);
            
            JsonNode repoList = queryArtifactoryPath(basePath);
            
            if (repoList == null || !repoList.has("children")) {
                System.out.println("ERROR: No data found at base path: " + basePath);
                return releases;
            }
            
            for (JsonNode repoNode : repoList.get("children")) {
                String repoName = repoNode.get("uri").asText().replace("/", "");
                
                if (repositoryFilter != null && !repositoryFilter.isEmpty()
                    && !repoName.toLowerCase().contains(repositoryFilter.toLowerCase())) {
                    continue;
                }
                
                // Query repository/{environment}/
                String repoPath = basePath + "/" + repoName;
                JsonNode envList = queryArtifactoryPath(repoPath);
                
                if (envList != null && envList.has("children")) {
                    for (JsonNode envNode : envList.get("children")) {
                        String envName = envNode.get("uri").asText().replace("/", "");
                        
                        if (environmentFilter != null && !environmentFilter.equals(envName)) {
                            continue;
                        }
                        
                        // Query repository/{environment}/pr-{number}/ or {version}/
                        // Supports both PR-based releases (preview) and version-based releases (staging/production)
                        String envPath = repoPath + "/" + envName;
                        JsonNode releaseList = queryArtifactoryPath(envPath);
                        
                        if (releaseList != null && releaseList.has("children")) {
                            for (JsonNode releaseNode : releaseList.get("children")) {
                                String releaseUri = releaseNode.get("uri").asText();
                                String cleanReleaseUri = releaseUri.replace("/", "");
                                
                                // Handle multiple formats:
                                // 1. pr-{number} (preview releases)
                                // 2. pr-{number}-{version} (preview with version)
                                // 3. v{version} or {version} (staging/production releases, e.g., "v1.0.0" or "1.0.0")
                                // 4. {number}-{version} (legacy format without pr- prefix)
                                Integer prNumber = null;
                                String version = null;
                                boolean isVersionBased = false;
                                
                                if (cleanReleaseUri.startsWith("pr-")) {
                                    // PR-based format: pr-{number} or pr-{number}-{version}
                                    // Handle double "pr-" prefix (e.g., "pr-pr-888-0.5.12")
                                    String afterPr = cleanReleaseUri.substring(3); // Remove first "pr-"
                                    if (afterPr.startsWith("pr-")) {
                                        // Double prefix, remove second "pr-" as well
                                        afterPr = afterPr.substring(3);
                                    }
                                    try {
                                        int dashIndex = afterPr.indexOf("-");
                                        if (dashIndex > 0) {
                                            // Format: pr-{number}-{version} or pr-pr-{number}-{version}
                                            prNumber = Integer.parseInt(afterPr.substring(0, dashIndex));
                                            version = afterPr.substring(dashIndex + 1);
                                        } else {
                                            // Format: pr-{number} or pr-pr-{number}
                                            prNumber = Integer.parseInt(afterPr);
                                        }
                                    } catch (NumberFormatException e) {
                                        System.err.println("Warning: Could not parse PR number from directory: " + cleanReleaseUri);
                                        continue;
                                    }
                                } else if (cleanReleaseUri.startsWith("v") && cleanReleaseUri.length() > 1) {
                                    // Version-based format: v{version} (e.g., "v1.0.0")
                                    isVersionBased = true;
                                    version = cleanReleaseUri.substring(1); // Remove "v" prefix
                                    // For version-based releases, use a synthetic PR number based on version hash
                                    prNumber = cleanReleaseUri.hashCode();
                                } else {
                                    // Try to parse as version (e.g., "1.0.0") or legacy format
                                    try {
                                        // Check if it's a pure version string (contains dots, e.g., "1.0.0")
                                        if (cleanReleaseUri.matches("^[0-9]+\\.[0-9]+(\\.[0-9]+)?(-.*)?$")) {
                                            isVersionBased = true;
                                            version = cleanReleaseUri;
                                            prNumber = cleanReleaseUri.hashCode();
                                        } else {
                                            // Legacy format without "pr-" prefix (e.g., "888-0.5.12")
                                            int dashIndex = cleanReleaseUri.indexOf("-");
                                            if (dashIndex > 0) {
                                                prNumber = Integer.parseInt(cleanReleaseUri.substring(0, dashIndex));
                                                version = cleanReleaseUri.substring(dashIndex + 1);
                                            } else {
                                                prNumber = Integer.parseInt(cleanReleaseUri);
                                            }
                                        }
                                    } catch (NumberFormatException e) {
                                        System.err.println("Warning: Could not parse release identifier from directory: " + cleanReleaseUri);
                                        continue;
                                    }
                                }
                                
                                if (prNumber == null) {
                                    continue; // Skip if we couldn't extract identifier
                                }
                                
                                // Get artifact files in this release directory
                                String releasePath = envPath + "/" + cleanReleaseUri;
                                JsonNode artifacts = queryArtifactoryPath(releasePath);
                                
                                ReleaseInfo release = new ReleaseInfo();
                                release.setRepository(repoName);
                                release.setEnvironment(envName);
                                release.setPrNumber(prNumber);
                                release.setReleaseDirectory(cleanReleaseUri); // Store the actual directory name
                                release.setArtifacts(extractArtifacts(artifacts));
                                
                                // Commit/branch are not in the JFrog directory layout; enriched later from artifacts
                                release.setCommitSha("unknown");
                                release.setBranch("unknown");
                                // Use extracted version if available, otherwise try to extract from directory name
                                if (version != null && !version.isEmpty()) {
                                    release.setReleaseTag(version);
                                } else if (isVersionBased && cleanReleaseUri.startsWith("v")) {
                                    // For version-based releases starting with "v", use the full identifier
                                    release.setReleaseTag(cleanReleaseUri);
                                } else {
                                    release.setReleaseTag(extractReleaseTag("unknown"));
                                }
                                
                                // Extract creation timestamp from JFrog API response
                                LocalDateTime createdAt = extractTimestamp(artifacts, "created");
                                if (createdAt == null) {
                                    createdAt = extractTimestamp(artifacts, "lastModified");
                                }
                                if (createdAt == null) {
                                    createdAt = extractTimestamp(artifacts, "modified");
                                }
                                
                                // If directory doesn't have timestamp, try to get from first artifact file
                                if (createdAt == null && artifacts != null && artifacts.has("children") && 
                                    artifacts.get("children").size() > 0) {
                                    JsonNode firstChild = artifacts.get("children").get(0);
                                    String firstFileName = firstChild.get("uri").asText().replace("/", "");
                                    String firstFilePath = releasePath + "/" + firstFileName;
                                    JsonNode firstFileInfo = queryArtifactoryPath(firstFilePath);
                                    if (firstFileInfo != null) {
                                        createdAt = extractTimestamp(firstFileInfo, "created");
                                        if (createdAt == null) {
                                            createdAt = extractTimestamp(firstFileInfo, "lastModified");
                                        }
                                    }
                                }
                                
                                if (createdAt != null) {
                                    release.setCreatedAt(createdAt);
                                    release.setUpdatedAt(createdAt);
                                }
                                
                                releases.add(release);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error querying JFrog: " + e.getMessage());
        }
        
        // Sort by repository, environment, then PR number (descending)
        releases.sort((a, b) -> {
            int repoCompare = a.getRepository().compareTo(b.getRepository());
            if (repoCompare != 0) return repoCompare;
            int envCompare = a.getEnvironment().compareTo(b.getEnvironment());
            if (envCompare != 0) return envCompare;
            return b.getPrNumber().compareTo(a.getPrNumber());
        });
        
        return releases;
    }
    
    private JsonNode queryArtifactoryPath(String path) {
        try {
            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            
            String response = getWebClient().get()
                .uri("/api/storage/{path}", cleanPath)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), clientResponse -> {
                    System.err.println("HTTP Error: " + clientResponse.statusCode() + " for path: " + cleanPath);
                    return clientResponse.bodyToMono(String.class)
                        .doOnNext(body -> System.err.println("Error response body: " + body))
                        .then(Mono.error(new RuntimeException("HTTP " + clientResponse.statusCode())));
                })
                .bodyToMono(String.class)
                .block();
            
            if (response == null || response.trim().isEmpty()) {
                return null;
            }
            
            return objectMapper.readTree(response);
        } catch (Exception e) {
            System.err.println("Error querying JFrog path '" + path + "': " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract timestamp from JFrog API response
     * JFrog returns timestamps in ISO 8601 format (e.g., "2024-01-15T10:30:00.000Z")
     */
    private LocalDateTime extractTimestamp(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        
        try {
            String timestampStr = node.get(fieldName).asText();
            if (timestampStr == null || timestampStr.isEmpty()) {
                return null;
            }
            
            // JFrog returns ISO 8601 timestamps, try parsing with different formats
            try {
                // Try ISO 8601 with timezone
                if (timestampStr.endsWith("Z")) {
                    timestampStr = timestampStr.substring(0, timestampStr.length() - 1) + "+00:00";
                }
                // Parse ISO 8601 format
                return java.time.ZonedDateTime.parse(timestampStr, 
                    DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime();
            } catch (DateTimeParseException e) {
                // Try alternative format: "2024-01-15T10:30:00.000"
                try {
                    return LocalDateTime.parse(timestampStr, 
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
                } catch (DateTimeParseException e2) {
                    // Try without milliseconds
                    try {
                        return LocalDateTime.parse(timestampStr, 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                    } catch (DateTimeParseException e3) {
                        System.err.println("Failed to parse timestamp '" + timestampStr + "': " + e3.getMessage());
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting timestamp from field '" + fieldName + "': " + e.getMessage());
            return null;
        }
    }
    
    private Map<String, String> extractArtifacts(JsonNode artifactsNode) {
        Map<String, String> artifacts = new HashMap<>();
        
        if (artifactsNode != null && artifactsNode.has("children")) {
            String basePath = artifactsNode.has("path") ? artifactsNode.get("path").asText() : "";
            
            for (JsonNode fileNode : artifactsNode.get("children")) {
                String fileName = fileNode.get("uri").asText().replace("/", "");
                String filePath = basePath.isEmpty() ? fileName : basePath + "/" + fileName;
                
                // Map known artifact types
                if (fileName.contains("cyclonedx") || fileName.endsWith(".cdx.json")) {
                    artifacts.put("sbom_cyclonedx", filePath);
                } else if (fileName.contains("spdx") || fileName.endsWith(".spdx.json")) {
                    artifacts.put("sbom_spdx", filePath);
                } else if (fileName.contains("summary")) {
                    artifacts.put("sbom_summary", filePath);
                } else if (fileName.contains("release-notes") || fileName.contains("RELEASE_NOTES")) {
                    artifacts.put("release_notes", filePath);
                } else if (fileName.contains("trivy") && fileName.endsWith(".html")) {
                    artifacts.put("trivy_report", filePath);
                }
            }
        }
        
        return artifacts;
    }
    
    private String extractReleaseTag(String branch) {
        if (branch == null || branch.equals("unknown")) {
            return null;
        }
        
        java.util.regex.Pattern versionPattern = java.util.regex.Pattern.compile(
            "(?:v|version|release[-_]?)?([0-9]+\\.[0-9]+(?:\\.[0-9]+)?(?:-[a-zA-Z0-9]+)?)"
        );
        java.util.regex.Matcher matcher = versionPattern.matcher(branch);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    /**
     * Populate commit SHA and branch from release artifacts / deploy conventions when
     * directory discovery left them as "unknown".
     *
     * Commit SHA: prefers the full 40-char SHA from a Trivy report image tag; falls back to
     * {@code **Commit:**} in release-notes.md (often a short SHA).
     *
     * Branch: prefers {@code **Branch:**} in release-notes.md; otherwise infers from
     * environment ({@code staging}→{@code main}, {@code production}→{@code production}).
     */
    public void enrichCommitMetadata(ReleaseInfo release) {
        enrichCommitMetadata(release, false);
    }

    /**
     * @param notesOnly when true, skip Trivy download (faster bulk matching)
     */
    public void enrichCommitMetadata(ReleaseInfo release, boolean notesOnly) {
        enrichCommitMetadata(mentatLocation, release, notesOnly);
    }

    public void enrichCommitMetadata(JfrogLocation location, ReleaseInfo release, boolean notesOnly) {
        if (release == null) {
            return;
        }

        boolean needCommit = isUnknownCommitSha(release.getCommitSha());
        boolean needBranch = isUnknownBranch(release.getBranch());
        if (!needCommit && !needBranch) {
            return;
        }

        Map<String, String> artifacts = release.getArtifacts();
        if (artifacts == null) {
            artifacts = Map.of();
        }

        String notesContent = null;
        String notesPath = artifacts.get("release_notes");
        if (notesPath != null && !notesPath.isBlank() && (needCommit || needBranch)) {
            try {
                byte[] data = downloadArtifact(
                    location,
                    release.getRepository(),
                    release.getEnvironment(),
                    release.getPrNumber(),
                    release.getReleaseDirectory(),
                    notesPath
                );
                notesContent = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("Failed to read release notes for metadata enrichment: " + e.getMessage());
            }
        }

        if (needCommit) {
            String fromNotes = extractCommitShaFromReleaseNotes(notesContent);
            String fromTrivy = null;

            String trivyPath = artifacts.get("trivy_report");
            if (!notesOnly && trivyPath != null && !trivyPath.isBlank()) {
                try {
                    byte[] data = downloadArtifact(
                        location,
                        release.getRepository(),
                        release.getEnvironment(),
                        release.getPrNumber(),
                        release.getReleaseDirectory(),
                        trivyPath
                    );
                    // Title/image tag appear near the start of the HTML; only scan a prefix for speed
                    String html = new String(data, 0, Math.min(data.length, 16_384), java.nio.charset.StandardCharsets.UTF_8);
                    fromTrivy = extractCommitShaFromTrivyReport(html);
                } catch (Exception e) {
                    System.err.println("Failed to enrich commit SHA from Trivy report: " + e.getMessage());
                }
            }

            String resolved = preferCommitSha(fromTrivy, fromNotes);
            if (resolved != null) {
                release.setCommitSha(resolved);
            }
        }

        if (needBranch) {
            String fromNotes = extractBranchFromReleaseNotes(notesContent);
            String resolved = preferBranch(fromNotes, release.getEnvironment());
            if (resolved != null) {
                release.setBranch(resolved);
            }
        }
    }

    /** True if either value is a prefix of the other (short vs full SHA). */
    public static boolean hashesMatch(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String left = a.trim().toLowerCase();
        String right = b.trim().toLowerCase();
        if (left.isEmpty() || right.isEmpty()
            || "unknown".equals(left) || "unknown".equals(right)) {
            return false;
        }
        return left.equals(right) || left.startsWith(right) || right.startsWith(left);
    }

    static boolean isUnknownCommitSha(String commitSha) {
        return commitSha == null || commitSha.isBlank() || "unknown".equalsIgnoreCase(commitSha.trim());
    }

    static boolean isUnknownBranch(String branch) {
        return branch == null || branch.isBlank() || "unknown".equalsIgnoreCase(branch.trim());
    }

    /**
     * Prefer a full 40-char SHA when available; otherwise use the short SHA from release notes.
     */
    static String preferCommitSha(String fromTrivy, String fromNotes) {
        if (fromTrivy != null && !fromTrivy.isBlank()) {
            return fromTrivy;
        }
        if (fromNotes != null && !fromNotes.isBlank()) {
            return fromNotes;
        }
        return null;
    }

    /**
     * Prefer an explicit branch from release notes; otherwise map known deploy environments.
     */
    static String preferBranch(String fromNotes, String environment) {
        if (fromNotes != null && !fromNotes.isBlank()) {
            return fromNotes;
        }
        return inferBranchFromEnvironment(environment);
    }

    /**
     * Infer deploy branch from environment name based on platform CD conventions.
     */
    static String inferBranchFromEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            return null;
        }
        return switch (environment.trim().toLowerCase()) {
            case "staging" -> "main";
            case "production" -> "production";
            default -> null; // preview / unknown — leave as unknown
        };
    }

    /**
     * Parse {@code **Commit:** &lt;sha&gt;} from platform-workflows release notes markdown.
     */
    static String extractCommitShaFromReleaseNotes(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?im)^\\*\\*Commit:\\*\\*\\s*([0-9a-f]{7,40})\\b"
        );
        java.util.regex.Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Parse {@code **Branch:** &lt;name&gt;} from release notes when present.
     */
    static String extractBranchFromReleaseNotes(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?im)^\\*\\*Branch:\\*\\*\\s*(\\S+)\\s*$"
        );
        java.util.regex.Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Parse the deploy image tag SHA from a Trivy HTML report title, e.g.
     * {@code .../slingshot-simulation:74aeaeec9604ce5946d5a354ec4e837425a089eb (redhat ...)}.
     */
    static String extractCommitShaFromTrivyReport(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        // Prefer full SHA after an image tag colon
        java.util.regex.Pattern fullSha = java.util.regex.Pattern.compile(
            ":([0-9a-f]{40})\\b"
        );
        java.util.regex.Matcher fullMatcher = fullSha.matcher(html);
        if (fullMatcher.find()) {
            return fullMatcher.group(1);
        }
        return null;
    }
    
    public static class ReleaseInfo {
        private String repository;
        private String environment;
        private Integer prNumber;
        private String commitSha;
        private String branch;
        private String workflowRunId;
        private String releaseTag;
        private String releaseDirectory; // Store the actual directory name (e.g., "pr-123", "v1.0.0", "1.0.0")
        private Map<String, String> artifacts;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();
        
        // Getters and setters
        public String getRepository() { return repository; }
        public void setRepository(String repository) { this.repository = repository; }
        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }
        public Integer getPrNumber() { return prNumber; }
        public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }
        public String getCommitSha() { return commitSha; }
        public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getWorkflowRunId() { return workflowRunId; }
        public void setWorkflowRunId(String workflowRunId) { this.workflowRunId = workflowRunId; }
        public Map<String, String> getArtifacts() { return artifacts; }
        public void setArtifacts(Map<String, String> artifacts) { this.artifacts = artifacts; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public String getReleaseTag() { return releaseTag; }
        public void setReleaseTag(String releaseTag) { this.releaseTag = releaseTag; }
        public String getReleaseDirectory() { return releaseDirectory; }
        public void setReleaseDirectory(String releaseDirectory) { this.releaseDirectory = releaseDirectory; }
    }
}
