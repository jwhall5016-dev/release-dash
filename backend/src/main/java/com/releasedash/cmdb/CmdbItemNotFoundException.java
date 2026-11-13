package com.releasedash.cmdb;

public class CmdbItemNotFoundException extends RuntimeException {
    public CmdbItemNotFoundException(String ciNumber) {
        super("CMDB item not found: " + ciNumber);
    }
}
