package com.releasedash.cmdb;

public class CmdbNotImplementedException extends RuntimeException {
    public CmdbNotImplementedException() {
        super("CMDB GitHub integration is not implemented yet");
    }
}
