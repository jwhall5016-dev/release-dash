package com.releasedash.foobar;

import com.releasedash.service.JfrogLocation;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Foobar's Artifactory folder. Mentat uses {@code mentat.jfrog.repo-key} and {@code mentat.jfrog.prefix}.
 * Both {@code repoKey} and {@code prefix} must be set before {@code /foobar/api/releases} reads JFrog.
 */
@ConfigurationProperties(prefix = "foobar.jfrog")
public class FoobarJfrogProperties {

    private String repoKey = "";
    private String prefix = "";

    public boolean isConfigured() {
        return !JfrogLocation.strip(repoKey).isEmpty() && !JfrogLocation.strip(prefix).isEmpty();
    }

    public JfrogLocation location() {
        return new JfrogLocation(repoKey, prefix);
    }

    public String getRepoKey() {
        return repoKey;
    }

    public void setRepoKey(String repoKey) {
        this.repoKey = repoKey;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
