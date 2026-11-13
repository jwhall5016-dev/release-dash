package com.releasedash.cmdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CmdbPathsTest {

    @Test
    void mapsKnownCategories() {
        assertEquals("systems/MENTAT-SYS-02.yaml", CmdbPaths.filePathFor("SYS", "MENTAT-SYS-02"));
        assertEquals("environments/MENTAT-ENV-01.yaml", CmdbPaths.filePathFor("env", "MENTAT-ENV-01"));
        assertEquals("docs/MENTAT-DOC-01-PLAN-Rev000.yaml",
            CmdbPaths.filePathFor("DOC", "MENTAT-DOC-01-PLAN-Rev000"));
        assertEquals("releases/MENTAT-REL-01.yaml", CmdbPaths.filePathFor("REL", "MENTAT-REL-01"));
        assertEquals("demos/MENTAT-DMO-01.yaml", CmdbPaths.filePathFor("DMO", "MENTAT-DMO-01"));
        assertEquals("software/MENTAT-SSC-01.yaml", CmdbPaths.filePathFor("SSC", "MENTAT-SSC-01"));
    }

    @Test
    void rejectsUnknownCategoryAndUnsafeNames() {
        assertThrows(IllegalArgumentException.class, () -> CmdbPaths.filePathFor("APP", "MENTAT-SYS-01"));
        assertThrows(IllegalArgumentException.class, () -> CmdbPaths.filePathFor("SYS", "../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> CmdbPaths.filePathFor("SYS", "foo/bar"));
        assertThrows(IllegalArgumentException.class, () -> CmdbPaths.directoryFor(""));
    }
}
