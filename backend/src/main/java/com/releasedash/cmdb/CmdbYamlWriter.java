package com.releasedash.cmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Serializes a CI record to per-file YAML. Never writes {@code cmdb/cis.yaml}. */
public final class CmdbYamlWriter {

    private static final ObjectMapper YAML = new ObjectMapper(
        new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
    );

    private CmdbYamlWriter() {
    }

    public static String toYaml(CmdbItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", trimRequired(item.getName(), "name"));
        String category = trimRequired(item.getCategory(), "category").toUpperCase(Locale.ROOT);
        map.put("category", category);
        map.put("ci_number", trimRequired(item.getCiNumber(), "ci_number"));
        String parent = item.getParent() == null ? null : item.getParent().trim();
        map.put("parent", parent == null || parent.isEmpty() ? null : parent);
        map.put("status", trimRequired(item.getStatus(), "status"));
        if ("DOC".equals(category)) {
            map.put("doc_type", item.getDocType());
            map.put("revision", item.getRevision());
        }
        if (item.getNotes() != null && !item.getNotes().isBlank()) {
            map.put("notes", item.getNotes().trim());
        }
        try {
            return YAML.writeValueAsString(map);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to serialize CI YAML: " + e.getMessage(), e);
        }
    }

    private static String trimRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
