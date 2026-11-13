package com.releasedash.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JfrogLocationTest {

    @Test
    void storageRootJoinsRepoKeyAndPrefix() {
        assertEquals(
            "ssa-generic/generic-release-artifacts",
            new JfrogLocation("ssa-generic", "generic-release-artifacts").storageRoot()
        );
        assertEquals(
            "ssa-generic/foobar-release-artifacts",
            new JfrogLocation("/ssa-generic/", "/foobar-release-artifacts/").storageRoot()
        );
    }
}
