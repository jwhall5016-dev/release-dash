package com.releasedash.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JfrogServiceCommitShaTest {

    @Test
    void extractCommitShaFromReleaseNotes_parsesShortSha() {
        String notes = """
            ## Release Notes

            **Commit:** 74aeaee

            **Author:** @jimmy-slingshot
            """;

        assertEquals("74aeaee", JfrogService.extractCommitShaFromReleaseNotes(notes));
    }

    @Test
    void extractCommitShaFromReleaseNotes_parsesFullSha() {
        String notes = """
            ## Release Notes

            **Commit:** 74aeaeec9604ce5946d5a354ec4e837425a089eb
            """;

        assertEquals(
            "74aeaeec9604ce5946d5a354ec4e837425a089eb",
            JfrogService.extractCommitShaFromReleaseNotes(notes)
        );
    }

    @Test
    void extractCommitShaFromReleaseNotes_returnsNullWhenMissing() {
        assertNull(JfrogService.extractCommitShaFromReleaseNotes("## Release Notes\n\nNo commit here"));
        assertNull(JfrogService.extractCommitShaFromReleaseNotes(null));
        assertNull(JfrogService.extractCommitShaFromReleaseNotes(""));
    }

    @Test
    void extractCommitShaFromTrivyReport_parsesImageTagSha() {
        String html = """
            <!DOCTYPE html>
            <html>
              <head>
                <title>slingshotaerospace.jfrog.io/plat-docker/slingshot-simulation:74aeaeec9604ce5946d5a354ec4e837425a089eb (redhat 9.8) - Trivy Report</title>
              </head>
              <body>
                <h1>slingshotaerospace.jfrog.io/plat-docker/slingshot-simulation:74aeaeec9604ce5946d5a354ec4e837425a089eb (redhat 9.8)</h1>
              </body>
            </html>
            """;

        assertEquals(
            "74aeaeec9604ce5946d5a354ec4e837425a089eb",
            JfrogService.extractCommitShaFromTrivyReport(html)
        );
    }

    @Test
    void extractCommitShaFromTrivyReport_returnsNullWhenMissing() {
        assertNull(JfrogService.extractCommitShaFromTrivyReport("<html><title>no sha</title></html>"));
        assertNull(JfrogService.extractCommitShaFromTrivyReport(null));
    }

    @Test
    void preferCommitSha_prefersFullTrivySha() {
        assertEquals(
            "74aeaeec9604ce5946d5a354ec4e837425a089eb",
            JfrogService.preferCommitSha("74aeaeec9604ce5946d5a354ec4e837425a089eb", "74aeaee")
        );
        assertEquals("74aeaee", JfrogService.preferCommitSha(null, "74aeaee"));
        assertNull(JfrogService.preferCommitSha(null, null));
    }

    @Test
    void isUnknownCommitSha() {
        assertTrue(JfrogService.isUnknownCommitSha(null));
        assertTrue(JfrogService.isUnknownCommitSha(""));
        assertTrue(JfrogService.isUnknownCommitSha("unknown"));
        assertTrue(JfrogService.isUnknownCommitSha("UNKNOWN"));
        assertFalse(JfrogService.isUnknownCommitSha("74aeaee"));
    }

    @Test
    void extractBranchFromReleaseNotes_parsesExplicitBranch() {
        String notes = """
            ## Release Notes

            **Commit:** 74aeaee

            **Branch:** feature/tvsim-123

            **Author:** @jimmy-slingshot
            """;

        assertEquals("feature/tvsim-123", JfrogService.extractBranchFromReleaseNotes(notes));
    }

    @Test
    void extractBranchFromReleaseNotes_returnsNullWhenMissing() {
        String notes = """
            ## Release Notes

            **Commit:** 74aeaee
            """;
        assertNull(JfrogService.extractBranchFromReleaseNotes(notes));
        assertNull(JfrogService.extractBranchFromReleaseNotes(null));
    }

    @Test
    void inferBranchFromEnvironment() {
        assertEquals("main", JfrogService.inferBranchFromEnvironment("staging"));
        assertEquals("production", JfrogService.inferBranchFromEnvironment("production"));
        assertNull(JfrogService.inferBranchFromEnvironment("preview"));
        assertNull(JfrogService.inferBranchFromEnvironment(null));
    }

    @Test
    void preferBranch_prefersNotesOverEnvironment() {
        assertEquals("feature/x", JfrogService.preferBranch("feature/x", "staging"));
        assertEquals("main", JfrogService.preferBranch(null, "staging"));
        assertNull(JfrogService.preferBranch(null, "preview"));
    }
}
