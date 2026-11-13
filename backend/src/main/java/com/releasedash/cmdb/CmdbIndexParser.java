package com.releasedash.cmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Parses the generated {@code cmdb/cis.yaml} index. No GitHub I/O. */
public final class CmdbIndexParser {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private CmdbIndexParser() {
    }

    public static List<CmdbItem> parse(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return List.of();
        }
        try {
            CmdbIndexDocument document = YAML_MAPPER.readValue(yaml, CmdbIndexDocument.class);
            if (document == null || document.getItems() == null) {
                return List.of();
            }
            return new ArrayList<>(document.getItems());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse CMDB index YAML: " + e.getMessage(), e);
        }
    }
}
