package com.releasedash.cmdb;

public class CmdbNotEnabledException extends RuntimeException {
    public CmdbNotEnabledException() {
        super("CMDB is not enabled");
    }
}
