package com.releasedash.cmdb;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CmdbIndexParserTest {

    @Test
    void parseSnapshotIndex() throws IOException {
        String yaml = loadFixture("cmdb/cis-snapshot.yaml");
        List<CmdbItem> items = CmdbIndexParser.parse(yaml);

        assertEquals(5, items.size());
        CmdbItem talos = items.stream()
            .filter(item -> "MENTAT-SYS-01.A".equals(item.getCiNumber()))
            .findFirst()
            .orElseThrow();
        assertEquals("TALOS", talos.getName());
        assertEquals("SYS", talos.getCategory());
        assertEquals("MENTAT-SYS-01", talos.getParent());

        CmdbItem plan = items.stream()
            .filter(item -> "MENTAT-DOC-01-PLAN-Rev000".equals(item.getCiNumber()))
            .findFirst()
            .orElseThrow();
        assertEquals("PLAN", plan.getDocType());
        assertEquals(0, plan.getRevision());
    }

    @Test
    void parseIgnoresUnknownFields() {
        String yaml = """
            items:
              - name: Extra
                category: SYS
                ci_number: MENTAT-SYS-99
                parent: null
                status: active
                future_field: ignored
            """;
        List<CmdbItem> items = CmdbIndexParser.parse(yaml);
        assertEquals(1, items.size());
        assertEquals("MENTAT-SYS-99", items.get(0).getCiNumber());
    }

    @Test
    void parseEmptyYamlReturnsEmptyList() {
        assertTrue(CmdbIndexParser.parse("").isEmpty());
        assertTrue(CmdbIndexParser.parse("items: []").isEmpty());
    }

    @Test
    void parseInvalidYamlThrows() {
        assertThrows(IllegalArgumentException.class, () -> CmdbIndexParser.parse("items: ["));
    }

    private static String loadFixture(String path) throws IOException {
        try (var in = Objects.requireNonNull(
            CmdbIndexParserTest.class.getClassLoader().getResourceAsStream(path),
            path
        )) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
