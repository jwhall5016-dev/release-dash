package com.releasedash.cmdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Serves the bundled CI JSON Schema snapshot. No GitHub I/O.
 * Refresh {@code classpath:cmdb/ci-record.schema.json} after the CMDB repo lands.
 */
@Service
public class CmdbSchemaService {

    static final String SNAPSHOT_RESOURCE = "/cmdb/ci-record.schema.json";

    private final JsonNode snapshot;

    public CmdbSchemaService(ObjectMapper objectMapper) {
        try (InputStream in = CmdbSchemaService.class.getResourceAsStream(SNAPSHOT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + SNAPSHOT_RESOURCE);
            }
            this.snapshot = objectMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load CMDB schema snapshot", e);
        }
    }

    public JsonNode snapshot() {
        return snapshot;
    }
}
