package com.releasedash.cmdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CmdbYamlWriterTest {

    @Test
    void writesStableFieldOrderAndNullParent() {
        CmdbItem item = new CmdbItem();
        item.setName("TALOS");
        item.setCategory("SYS");
        item.setCiNumber("MENTAT-SYS-01.A");
        item.setParent(null);
        item.setStatus("awaiting approval");
        item.setNotes("Proposed CI");
        String yaml = CmdbYamlWriter.toYaml(item);
        assertTrue(yaml.startsWith("name: TALOS"));
        assertTrue(yaml.contains("ci_number: MENTAT-SYS-01.A"));
        assertTrue(yaml.contains("parent: null") || yaml.contains("parent:"));
        assertTrue(!yaml.contains("doc_type"));
    }

    @Test
    void includesDocFields() {
        CmdbItem item = new CmdbItem();
        item.setName("SEMP");
        item.setCategory("DOC");
        item.setCiNumber("MENTAT-DOC-01-PLAN-Rev000");
        item.setParent(null);
        item.setStatus("pre-baseline");
        item.setDocType("PLAN");
        item.setRevision(0);
        String yaml = CmdbYamlWriter.toYaml(item);
        assertTrue(yaml.contains("doc_type: PLAN"));
        assertTrue(yaml.contains("revision: 0"));
    }
}
