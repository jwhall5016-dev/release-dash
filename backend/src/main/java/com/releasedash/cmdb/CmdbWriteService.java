package com.releasedash.cmdb;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class CmdbWriteService {

    private final CmdbProperties properties;
    private final CmdbCatalogService catalogService;
    private final CmdbGithubClient githubClient;

    public CmdbWriteService(
            CmdbProperties properties,
            CmdbCatalogService catalogService,
            CmdbGithubClient githubClient) {
        this.properties = properties;
        this.catalogService = catalogService;
        this.githubClient = githubClient;
    }

    public CmdbWriteResult create(CmdbItem item) {
        item.setStatus("awaiting approval");
        CmdbItemValidator.requireValid(item);
        String path;
        try {
            path = CmdbPaths.filePathFor(item.getCategory(), item.getCiNumber());
        } catch (IllegalArgumentException e) {
            throw new CmdbValidationException(List.of(e.getMessage()));
        }
        String ciNumber = item.getCiNumber().trim();
        boolean existsInIndex = catalogService.list(null, null).getItems().stream()
            .anyMatch(existing -> ciNumber.equals(existing.getCiNumber()));
        if (existsInIndex || githubClient.fileExists(path)) {
            throw new CmdbConflictException("A CI with number " + ciNumber + " already exists");
        }
        String yaml = CmdbYamlWriter.toYaml(item);
        String message = "chore(cmdb): add " + ciNumber;
        String mode = writeMode();
        if ("commit".equals(mode)) {
            githubClient.putFile(path, yaml, message, githubClient.defaultBranch());
            CmdbWriteResult result = new CmdbWriteResult();
            result.setPath(path);
            result.setMode("commit");
            result.setBranch(githubClient.defaultBranch());
            return result;
        }
        String branch = branchName(ciNumber, item.getName());
        String baseSha = githubClient.defaultBranchSha();
        githubClient.createBranch(branch, baseSha);
        githubClient.putFile(path, yaml, message, branch);
        String prBody = """
            Propose new configuration item `%s`.

            - Name: %s
            - Category: %s
            - Status: %s
            - Path: `%s`

            Review and merge this PR in GitHub. After merge, the CMDB sync Action rebuilds `cmdb/cis.yaml`.
            The catalog list may lag until that Action finishes (or Refresh is used).
            """.formatted(
            ciNumber,
            item.getName().trim(),
            item.getCategory().trim().toUpperCase(Locale.ROOT),
            item.getStatus().trim(),
            path
        );
        CmdbWriteResult result = githubClient.createPullRequest(
            message,
            prBody,
            branch,
            githubClient.defaultBranch()
        );
        result.setPath(path);
        result.setMode("pr");
        return result;
    }

    static String branchName(String ciNumber, String name) {
        String id = ciNumber.trim().replace('.', '-');
        String slug = name == null ? "" : name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (slug.length() > 32) {
            slug = slug.substring(0, 32).replaceAll("-+$", "");
        }
        String branch = slug.isEmpty() ? "cmdb/" + id : "cmdb/" + id + "-" + slug;
        return branch.length() > 200 ? branch.substring(0, 200) : branch;
    }

    private String writeMode() {
        if (properties.getWrite() == null || properties.getWrite().getMode() == null) {
            return "pr";
        }
        String mode = properties.getWrite().getMode().trim().toLowerCase(Locale.ROOT);
        return mode.isEmpty() ? "pr" : mode;
    }
}
