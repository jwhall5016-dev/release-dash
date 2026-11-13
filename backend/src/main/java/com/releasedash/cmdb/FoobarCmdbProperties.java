package com.releasedash.cmdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Foobar's CMDB repo, {@code slingshot-aerospace-inc/foobarcmdb}. */
@ConfigurationProperties(prefix = "foobar.cmdb")
public class FoobarCmdbProperties {

    private CmdbProperties.Github github = new CmdbProperties.Github();
    private CmdbProperties.Index index = new CmdbProperties.Index();
    private int cacheTtlSeconds = 45;

    public CmdbProperties.Github getGithub() {
        return github;
    }

    public void setGithub(CmdbProperties.Github github) {
        this.github = github;
    }

    public CmdbProperties.Index getIndex() {
        return index;
    }

    public void setIndex(CmdbProperties.Index index) {
        this.index = index;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }
}
