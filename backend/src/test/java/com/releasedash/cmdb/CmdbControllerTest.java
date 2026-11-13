package com.releasedash.cmdb;

import com.releasedash.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CmdbController.class)
@Import({GlobalExceptionHandler.class, CmdbSchemaService.class})
class CmdbControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CmdbProperties properties;

    @MockBean
    private CmdbCatalogService catalogService;

    @MockBean
    private CmdbWriteService writeService;

    @Test
    void itemsReturns503WhenDisabled() throws Exception {
        when(properties.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/mentat/api/cmdb/items"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("CMDB is not enabled"));
    }

    @Test
    void refreshReturns503WhenDisabled() throws Exception {
        when(properties.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/mentat/api/cmdb/refresh"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("CMDB is not enabled"));
    }

    @Test
    void itemsReturnsCatalogWhenEnabled() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        CmdbItem item = new CmdbItem();
        item.setName("TALOS");
        item.setCategory("SYS");
        item.setCiNumber("MENTAT-SYS-01.A");
        item.setStatus("awaiting approval");
        CmdbItemsResponse response = new CmdbItemsResponse(
            List.of(item),
            Instant.parse("2026-09-08T18:00:00Z"),
            "abc123",
            "https://github.com/slingshot-aerospace-inc/mentatcmdb/blob/main/cmdb/cis.yaml"
        );
        when(catalogService.list(null, null)).thenReturn(response);

        mockMvc.perform(get("/mentat/api/cmdb/items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].ci_number").value("MENTAT-SYS-01.A"))
            .andExpect(jsonPath("$.sha").value("abc123"));
    }

    @Test
    void schemaReturnsSnapshotEvenWhenDisabled() throws Exception {
        when(properties.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/mentat/api/cmdb/schema"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.properties.category.enum[0]").value("SYS"))
            .andExpect(jsonPath("$.properties.status.enum").isArray())
            .andExpect(jsonPath("$.properties.ci_number.pattern").isString());
    }

    @Test
    void createReturnsPrWhenEnabled() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        CmdbWriteResult result = new CmdbWriteResult();
        result.setPath("systems/MENTAT-SYS-03.yaml");
        result.setMode("pr");
        result.setPrUrl("https://github.com/slingshot-aerospace-inc/mentatcmdb/pull/1");
        result.setPrNumber(1);
        when(writeService.create(org.mockito.ArgumentMatchers.any())).thenReturn(result);

        mockMvc.perform(post("/mentat/api/cmdb/items")
                .contentType("application/json")
                .content("{\"name\":\"Release Dash\",\"category\":\"SYS\",\"ci_number\":\"MENTAT-SYS-03\",\"parent\":null,\"status\":\"awaiting approval\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pr_url").value("https://github.com/slingshot-aerospace-inc/mentatcmdb/pull/1"))
            .andExpect(jsonPath("$.path").value("systems/MENTAT-SYS-03.yaml"));
    }
}
