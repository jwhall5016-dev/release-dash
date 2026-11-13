package com.releasedash.cmdb;

public class CmdbGithubException extends RuntimeException {
    private final int status;

    public CmdbGithubException(String message) {
        this(message, 0, null);
    }

    public CmdbGithubException(String message, int status) {
        this(message, status, null);
    }

    public CmdbGithubException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    public CmdbGithubException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public boolean isNotFound() {
        return status == 404;
    }
}
