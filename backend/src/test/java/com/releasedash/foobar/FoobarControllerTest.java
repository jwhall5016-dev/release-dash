package com.releasedash.foobar;

import com.releasedash.cmdb.CmdbItem;
import com.releasedash.cmdb.CmdbItemsResponse;
import com.releasedash.cmdb.FoobarCmdbCatalog;
import com.releasedash.dto.StatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FoobarController.class)
class FoobarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FoobarCmdbCatalog cmdbCatalog;

    @MockBean
    private FoobarReleaseCatalog releaseCatalog;

    @BeforeEach
    void emptyFoobarCatalogs() {
        when(cmdbCatalog.list()).thenReturn(new CmdbItemsResponse(List.of(), null, null, null));
        when(releaseCatalog.stats()).thenReturn(new StatsResponse(0L, List.of(), List.of()));
        when(releaseCatalog.list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of());
    }

    @Test
    void foobarApiIsMountedBesideMentat() throws Exception {
        mockMvc.perform(get("/foobar/api"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.team").value("foobar"))
            .andExpect(jsonPath("$.api").value("/foobar/api"));
    }

    @Test
    void statsAndReleasesMatchTheMentatContract() throws Exception {
        mockMvc.perform(get("/foobar/api/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalReleases").value(0))
            .andExpect(jsonPath("$.repositories").isArray())
            .andExpect(jsonPath("$.environments").isArray());

        mockMvc.perform(get("/foobar/api/releases"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/foobar/api/live-services"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.services").isArray());

        mockMvc.perform(get("/foobar/api/cmdb/items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void cmdbItemsComeFromTheFoobarCatalog() throws Exception {
        CmdbItem item = new CmdbItem();
        item.setName("Widget");
        item.setCiNumber("FOOBAR-SYS-01");
        item.setStatus("active");
        when(cmdbCatalog.list()).thenReturn(new CmdbItemsResponse(List.of(item), null, null, null));

        mockMvc.perform(get("/foobar/api/cmdb/items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].ci_number").value("FOOBAR-SYS-01"))
            .andExpect(jsonPath("$.items[0].name").value("Widget"));
    }
}
