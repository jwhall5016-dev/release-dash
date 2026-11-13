package com.releasedash.cmdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes files in a CMDB GitHub repo from server configuration.
 * Owner and repo come from {@link CmdbProperties}, never from a request.
 * Uses its own WebClient so JFrog Basic Auth on the shared builder cannot leak onto GitHub calls.
 */
@Component
public class CmdbGithubClient {

    private static final String GITHUB_API = "https://api.github.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final CmdbProperties properties;
    private final ObjectMapper objectMapper;
    private final CmdbGithubAppAuth appAuth;
    private final WebClient.Builder webClientBuilder = WebClient.builder();

    public CmdbGithubClient(CmdbProperties properties, ObjectMapper objectMapper, CmdbGithubAppAuth appAuth) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.appAuth = appAuth;
    }

    public CmdbGithubFile fetchFile(String path) {
        return fetchFile(properties.getGithub(), path);
    }

    /**
     * Read {@code path} from the repo in {@code github}.
     */
    public CmdbGithubFile fetchFile(CmdbProperties.Github github, String path) {
        Session session = session(github);
        String branch = branchOf(github);
        String contentsPath = requirePath(path);
        URI uri = contentsUri(session.owner, session.repo, contentsPath, branch);
        return readFile(session, contentsPath, branch).orElseThrow(() -> new CmdbGithubException(
            "GitHub returned 404 for " + uri
                + " (missing file, or the token cannot read this private repo)",
            404));
    }

    public Optional<CmdbGithubFile> fetchFile(String path, String ref) {
        return readFile(session(), requirePath(path), ref);
    }

    private Optional<CmdbGithubFile> readFile(Session session, String contentsPath, String ref) {
        URI uri = contentsUri(session.owner, session.repo, contentsPath, ref);
        JsonNode body;
        try {
            body = exchange(session, HttpMethod.GET, uri, null);
        } catch (CmdbGithubException e) {
            if (e.isNotFound()) {
                return Optional.empty();
            }
            throw e;
        }
        if (body == null || body.path("content").isMissingNode() || body.path("content").isNull()) {
            return Optional.empty();
        }
        String encoding = body.path("encoding").asText("base64").toLowerCase(Locale.ROOT);
        if (!"base64".equals(encoding)) {
            throw new CmdbGithubException("Unsupported GitHub content encoding: " + encoding);
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(body.path("content").asText().replaceAll("\\s", ""));
            String text = new String(decoded, StandardCharsets.UTF_8);
            return Optional.of(new CmdbGithubFile(
                text,
                textOrNull(body, "sha"),
                textOrNull(body, "html_url")
            ));
        } catch (IllegalArgumentException e) {
            throw new CmdbGithubException("GitHub file content was not valid base64", e);
        }
    }

    public boolean fileExists(String path) {
        return fetchFile(path, defaultBranch()).isPresent();
    }

    public String defaultBranchSha() {
        Session session = session();
        String branch = defaultBranch();
        URI uri = gitRefUri(session.owner, session.repo, branch);
        JsonNode body = exchange(session, HttpMethod.GET, uri, null);
        String sha = body.path("object").path("sha").asText(null);
        if (sha == null || sha.isBlank()) {
            throw new CmdbGithubException("GitHub did not return a SHA for branch " + branch);
        }
        return sha;
    }

    public void createBranch(String branch, String fromSha) {
        Session session = session();
        URI uri = UriComponentsBuilder.fromUriString(GITHUB_API)
            .pathSegment("repos", session.owner, session.repo, "git", "refs")
            .build()
            .encode()
            .toUri();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("ref", "refs/heads/" + branch);
        body.put("sha", fromSha);
        exchange(session, HttpMethod.POST, uri, body);
    }

    public void putFile(String path, String content, String message, String branch) {
        Session session = session();
        String contentsPath = requirePath(path);
        URI uri = contentsUri(session.owner, session.repo, contentsPath);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        body.put("branch", branch);
        exchange(session, HttpMethod.PUT, uri, body);
    }

    public CmdbWriteResult createPullRequest(String title, String body, String head, String base) {
        Session session = session();
        URI uri = UriComponentsBuilder.fromUriString(GITHUB_API)
            .pathSegment("repos", session.owner, session.repo, "pulls")
            .build()
            .encode()
            .toUri();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("head", head);
        payload.put("base", base);
        JsonNode response = exchange(session, HttpMethod.POST, uri, payload);
        CmdbWriteResult result = new CmdbWriteResult();
        result.setMode("pr");
        result.setBranch(head);
        result.setPrUrl(textOrNull(response, "html_url"));
        if (response.path("number").canConvertToInt()) {
            result.setPrNumber(response.path("number").asInt());
        }
        return result;
    }

    public String defaultBranch() {
        return branchOf(properties.getGithub());
    }

    private static String branchOf(CmdbProperties.Github github) {
        String branch = trimToEmpty(github == null ? null : github.getBranch());
        return branch.isEmpty() ? "main" : branch;
    }

    private JsonNode exchange(Session session, HttpMethod method, URI uri, Object body) {
        HttpClient httpClient = HttpClient.create().responseTimeout(TIMEOUT);
        WebClient client = webClientBuilder.clone()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        try {
            var spec = client.method(method)
                .uri(uri)
                .headers(headers -> {
                    headers.setBearerAuth(session.token);
                    headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
                    headers.set(HttpHeaders.USER_AGENT, "release-dash");
                    headers.set("X-GitHub-Api-Version", "2022-11-28");
                });
            String json = (body == null ? spec : spec.bodyValue(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(msg -> reactor.core.publisher.Mono.error(
                        new CmdbGithubException(
                            statusMessage(response.statusCode(), uri, msg),
                            response.statusCode().value()))))
                .bodyToMono(String.class)
                .block(TIMEOUT);
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(json);
        } catch (CmdbGithubException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new CmdbGithubException(
                statusMessage(e.getStatusCode(), uri, e.getResponseBodyAsString()),
                e.getStatusCode().value(),
                e);
        } catch (Exception e) {
            CmdbGithubException githubEx = unwrapGithub(e);
            if (githubEx != null) {
                throw githubEx;
            }
            throw new CmdbGithubException("Failed to call GitHub: " + e.getMessage(), e);
        }
    }

    private Session session() {
        return session(properties.getGithub());
    }

    private Session session(CmdbProperties.Github github) {
        String owner = trimToEmpty(github == null ? null : github.getOwner());
        String repo = trimToEmpty(github == null ? null : github.getRepo());
        if (owner.isEmpty() || repo.isEmpty()) {
            throw new CmdbGithubException("CMDB GitHub owner/repo is not configured");
        }
        String token = appAuth.bearerToken(github);
        return new Session(owner, repo, token);
    }

    private static String requirePath(String path) {
        if (path == null || path.isBlank() || path.contains("..")) {
            throw new CmdbGithubException("Invalid CMDB Git path");
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    static URI contentsUri(String owner, String repo, String path, String ref) {
        return UriComponentsBuilder.fromUriString(GITHUB_API)
            .pathSegment("repos", owner, repo, "contents")
            .pathSegment(path.split("/"))
            .queryParam("ref", ref)
            .build()
            .encode()
            .toUri();
    }

    static URI contentsUri(String owner, String repo, String path) {
        return UriComponentsBuilder.fromUriString(GITHUB_API)
            .pathSegment("repos", owner, repo, "contents")
            .pathSegment(path.split("/"))
            .build()
            .encode()
            .toUri();
    }

    static URI gitRefUri(String owner, String repo, String branch) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(GITHUB_API)
            .pathSegment("repos", owner, repo, "git", "ref", "heads");
        for (String part : branch.split("/")) {
            builder = builder.pathSegment(part);
        }
        return builder.build().encode().toUri();
    }

    private static CmdbGithubException unwrapGithub(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof CmdbGithubException githubEx) {
                return githubEx;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String statusMessage(HttpStatusCode status, URI uri, String body) {
        int code = status.value();
        if (code == 401 || code == 403) {
            return "CMDB GitHub credentials rejected or missing Contents / Pull requests permission";
        }
        if (code == 404) {
            return "GitHub returned 404 for " + uri
                + " (missing file, or the token cannot read this private repo)";
        }
        String snippet = body == null ? "" : body.strip();
        if (snippet.length() > 240) {
            snippet = snippet.substring(0, 240);
        }
        return "GitHub API error HTTP " + code + (snippet.isEmpty() ? "" : ": " + snippet);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record Session(String owner, String repo, String token) {
    }
}
