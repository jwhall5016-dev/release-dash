package com.releasedash.cmdb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CmdbCatalogServiceTest {

    @Test
    void listFiltersByCategory() {
        CmdbGithubClient client = mock(CmdbGithubClient.class);
        when(client.fetchFile("cmdb/cis.yaml")).thenReturn(new CmdbGithubFile("""
            items:
              - name: TALOS
                category: SYS
                ci_number: MENTAT-SYS-01.A
                status: awaiting approval
              - name: Preview
                category: ENV
                ci_number: MENTAT-ENV-01
                status: awaiting approval
            """, "sha1", "https://example.com/cis.yaml"));

        CmdbProperties properties = new CmdbProperties();
        properties.getGithub().setOwner("slingshot-aerospace-inc");
        properties.getGithub().setRepo("mentatcmdb");
        properties.getCache().setTtlSeconds(60);

        CmdbCatalogService service = new CmdbCatalogService(properties, client);
        CmdbItemsResponse sys = service.list("SYS", null);
        assertEquals(1, sys.getItems().size());
        assertEquals("MENTAT-SYS-01.A", sys.getItems().get(0).getCiNumber());
        assertEquals("sha1", sys.getSha());
    }

    @Test
    void getItemReturnsGitPath() {
        CmdbGithubClient client = mock(CmdbGithubClient.class);
        when(client.fetchFile("cmdb/cis.yaml")).thenReturn(new CmdbGithubFile("""
            items:
              - name: TALOS
                category: SYS
                ci_number: MENTAT-SYS-01.A
                status: awaiting approval
            """, "sha1", "https://example.com/cis.yaml"));

        CmdbProperties properties = new CmdbProperties();
        properties.getGithub().setOwner("slingshot-aerospace-inc");
        properties.getGithub().setRepo("mentatcmdb");
        properties.getGithub().setBranch("main");

        CmdbCatalogService service = new CmdbCatalogService(properties, client);
        CmdbItemView view = service.getItem("MENTAT-SYS-01.A");
        assertEquals("systems/MENTAT-SYS-01.A.yaml", view.getGitPath());
        assertEquals(
            "https://github.com/slingshot-aerospace-inc/mentatcmdb/blob/main/systems/MENTAT-SYS-01.A.yaml",
            view.getHtmlUrl()
        );
    }

    @Test
    void getItemMissingThrows() {
        CmdbGithubClient client = mock(CmdbGithubClient.class);
        when(client.fetchFile("cmdb/cis.yaml")).thenReturn(new CmdbGithubFile("items: []", "sha1", null));
        CmdbCatalogService service = new CmdbCatalogService(new CmdbProperties(), client);
        assertThrows(CmdbItemNotFoundException.class, () -> service.getItem("MENTAT-SYS-99"));
    }

    @Test
    void cacheReusedWithinTtl() {
        CmdbGithubClient client = mock(CmdbGithubClient.class);
        when(client.fetchFile("cmdb/cis.yaml")).thenReturn(
            new CmdbGithubFile("items: []", "sha1", null),
            new CmdbGithubFile("items: []", "sha2", null)
        );
        CmdbProperties properties = new CmdbProperties();
        properties.getCache().setTtlSeconds(60);
        CmdbCatalogService service = new CmdbCatalogService(properties, client);
        assertEquals("sha1", service.list(null, null).getSha());
        assertEquals("sha1", service.list(null, null).getSha());
        assertEquals("sha2", service.refresh().getSha());
    }
}
