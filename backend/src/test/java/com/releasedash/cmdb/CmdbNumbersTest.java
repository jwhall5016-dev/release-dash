package com.releasedash.cmdb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CmdbNumbersTest {

    @Test
    void nextSysNumberSkipsLetterAndNumericSuffixes() {
        String next = CmdbNumbers.nextNumber("SYS", List.of(
            "MENTAT-SYS-01",
            "MENTAT-SYS-01.A",
            "MENTAT-SYS-01.A.01",
            "MENTAT-SYS-02",
            "MENTAT-ENV-09"
        ));
        assertEquals("MENTAT-SYS-03", next);
    }

    @Test
    void nextDocNumberIgnoresRevisionTail() {
        assertEquals(
            "MENTAT-DOC-02",
            CmdbNumbers.nextNumber("DOC", List.of("MENTAT-DOC-01-PLAN-Rev000"))
        );
    }

    @Test
    void firstNumberWhenEmpty() {
        assertEquals("MENTAT-ENV-01", CmdbNumbers.nextNumber("ENV", List.of()));
        assertEquals("MENTAT-REL-01", CmdbNumbers.nextNumber("rel", null));
        assertEquals("MENTAT-SSC-01", CmdbNumbers.nextNumber("SSC", List.of()));
    }

    @Test
    void rejectsUnknownCategory() {
        assertThrows(IllegalArgumentException.class, () -> CmdbNumbers.nextNumber("APP", List.of()));
    }
}
