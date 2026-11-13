package com.releasedash.cmdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Mints a GitHub App installation token (Contents / PRs as granted on the App).
 * Falls back to {@code cmdb.github.token} when App ID + private key are not set.
 */
@Component
public class CmdbGithubAppAuth {

    private static final String GITHUB_API = "https://api.github.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Duration TOKEN_SKEW = Duration.ofMinutes(2);

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder = WebClient.builder();

    private final Object lock = new Object();
    private String cachedToken;
    private Instant cachedExpiresAt = Instant.EPOCH;
    private String cachedFor;

    public CmdbGithubAppAuth(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String bearerToken(CmdbProperties.Github github) {
        if (github == null) {
            throw new CmdbGithubException("CMDB GitHub token is not configured");
        }
        if (isAppConfigured(github)) {
            return installationToken(github);
        }
        String token = trimToEmpty(github.getToken());
        if (token.isEmpty()) {
            throw new CmdbGithubException("CMDB GitHub token is not configured");
        }
        return token;
    }

    static boolean isAppConfigured(CmdbProperties.Github github) {
        return github != null
            && !trimToEmpty(github.getAppId()).isEmpty()
            && !trimToEmpty(github.getPrivateKey()).isEmpty();
    }

    private String installationToken(CmdbProperties.Github github) {
        String owner = trimToEmpty(github.getOwner());
        String repo = trimToEmpty(github.getRepo());
        String cacheKey = trimToEmpty(github.getAppId()) + ":" + owner + "/" + repo
            + ":" + trimToEmpty(github.getInstallationId());
        synchronized (lock) {
            if (cachedToken != null
                && cacheKey.equals(cachedFor)
                && Instant.now().plus(TOKEN_SKEW).isBefore(cachedExpiresAt)) {
                return cachedToken;
            }
            String jwt = createAppJwt(github.getAppId(), github.getPrivateKey());
            String installationId = trimToEmpty(github.getInstallationId());
            if (installationId.isEmpty()) {
                installationId = discoverInstallationId(jwt, owner, repo);
            }
            JsonNode body = postJson(
                jwt,
                GITHUB_API + "/app/installations/" + installationId + "/access_tokens",
                "{}"
            );
            String token = body.path("token").asText("");
            if (token.isBlank()) {
                throw new CmdbGithubException("GitHub App installation token response had no token");
            }
            Instant expires = Instant.now().plus(Duration.ofMinutes(50));
            String expiresAt = body.path("expires_at").asText("");
            if (!expiresAt.isBlank()) {
                try {
                    expires = Instant.parse(expiresAt);
                } catch (Exception ignored) {
                    // keep default TTL
                }
            }
            cachedToken = token;
            cachedExpiresAt = expires;
            cachedFor = cacheKey;
            return token;
        }
    }

    String createAppJwt(String appId, String pem) {
        try {
            PrivateKey key = parsePrivateKey(pem);
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(trimToEmpty(appId))
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(9))))
                .build();
            SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
                claims
            );
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (CmdbGithubException e) {
            throw e;
        } catch (Exception e) {
            throw new CmdbGithubException("Unable to sign GitHub App JWT: " + e.getMessage(), e);
        }
    }

    static PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new CmdbGithubException("CMDB GitHub App private key is not configured");
        }
        String normalized = pem.replace("\\n", "\n").trim();
        boolean pkcs1 = normalized.contains("BEGIN RSA PRIVATE KEY");
        String body = normalized
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new CmdbGithubException("CMDB GitHub App private key is not valid base64", e);
        }
        if (pkcs1) {
            der = pkcs1ToPkcs8(der);
        }
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new CmdbGithubException("CMDB GitHub App private key could not be parsed", e);
        }
    }

    /** Wrap PKCS#1 RSAPrivateKey DER in a PKCS#8 PrivateKeyInfo. */
    static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        byte[] algorithm = new byte[] {
            0x30, 0x0d,
            0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
            0x05, 0x00
        };
        byte[] version = { 0x02, 0x01, 0x00 };
        byte[] octet = tlv((byte) 0x04, pkcs1);
        return tlv((byte) 0x30, concat(version, algorithm, octet));
    }

    private String discoverInstallationId(String jwt, String owner, String repo) {
        if (owner.isEmpty() || repo.isEmpty()) {
            throw new CmdbGithubException("CMDB GitHub owner/repo is not configured");
        }
        JsonNode body = getJson(jwt, GITHUB_API + "/repos/" + owner + "/" + repo + "/installation");
        String id = body.path("id").asText("");
        if (id.isBlank() && body.path("id").canConvertToLong()) {
            id = Long.toString(body.path("id").asLong());
        }
        if (id.isBlank()) {
            throw new CmdbGithubException("GitHub App is not installed on " + owner + "/" + repo);
        }
        return id;
    }

    private JsonNode getJson(String jwt, String url) {
        return parseJson(request(jwt).get().uri(url).retrieve());
    }

    private JsonNode postJson(String jwt, String url, String jsonBody) {
        return parseJson(
            request(jwt)
                .post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(jsonBody == null ? "{}" : jsonBody)
                .retrieve()
        );
    }

    private WebClient request(String jwt) {
        HttpClient httpClient = HttpClient.create().responseTimeout(TIMEOUT);
        return webClientBuilder.clone()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader(HttpHeaders.USER_AGENT, "release-dash")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    }

    private JsonNode parseJson(WebClient.ResponseSpec retrieve) {
        try {
            String json = retrieve
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(msg -> reactor.core.publisher.Mono.error(
                        new CmdbGithubException(
                            "GitHub App auth HTTP " + response.statusCode().value()
                                + (msg.isBlank() ? "" : ": " + msg.strip()),
                            response.statusCode().value()))))
                .bodyToMono(String.class)
                .block(TIMEOUT);
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(json);
        } catch (CmdbGithubException e) {
            throw e;
        } catch (Exception e) {
            CmdbGithubException githubEx = unwrap(e);
            if (githubEx != null) {
                throw githubEx;
            }
            throw new CmdbGithubException("GitHub App auth failed: " + e.getMessage(), e);
        }
    }

    private static CmdbGithubException unwrap(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof CmdbGithubException githubEx) {
                return githubEx;
            }
            current = current.getCause();
        }
        return null;
    }

    private static byte[] tlv(byte tag, byte[] value) {
        byte[] length = encodeLength(value.length);
        byte[] out = new byte[1 + length.length + value.length];
        out[0] = tag;
        System.arraycopy(length, 0, out, 1, length.length);
        System.arraycopy(value, 0, out, 1 + length.length, value.length);
        return out;
    }

    private static byte[] encodeLength(int length) {
        if (length < 128) {
            return new byte[] { (byte) length };
        }
        if (length < 256) {
            return new byte[] { (byte) 0x81, (byte) length };
        }
        return new byte[] { (byte) 0x82, (byte) (length >> 8), (byte) length };
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
