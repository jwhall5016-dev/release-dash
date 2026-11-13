package com.releasedash.config;

import com.releasedash.controller.RootController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RootController.class)
@Import(WebConfig.class)
class WebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uiRoutesServeTheMentatApp() throws Exception {
        mockMvc.perform(get("/mentat"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Release Dashboard")));

        mockMvc.perform(get("/mentat/cmdb"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Release Dashboard")));

        mockMvc.perform(get("/mentat/live"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Release Dashboard")));
    }

    @Test
    void foobarRoutesServeTheSameApp() throws Exception {
        mockMvc.perform(get("/foobar"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"root\"")));

        mockMvc.perform(get("/foobar/cmdb"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"root\"")));
    }

    @Test
    void foobarApiIsNotServedAsThePage() throws Exception {
        mockMvc.perform(get("/foobar/api"))
            .andExpect(status().isNotFound());
    }

    @Test
    void siteRootRedirectsToMentat() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/mentat"));

        mockMvc.perform(get("/spa/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"root\"")));
    }

    @Test
    void mentatApiIsNotServedAsTheDashboard() throws Exception {
        mockMvc.perform(get("/mentat/api/stats"))
            .andExpect(status().isNotFound());
    }

    @Test
    void healthcheckStaysAtTheRoot() throws Exception {
        mockMvc.perform(get("/healthcheck"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("healthy")));
    }
}
