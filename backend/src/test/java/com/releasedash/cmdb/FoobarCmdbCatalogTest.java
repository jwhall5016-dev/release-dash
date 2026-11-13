package com.releasedash.cmdb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FoobarCmdbCatalogTest {

    @Test
    void blankTokenDoesNotCallGithub() {
        CmdbGithubClient client = mock(CmdbGithubClient.class);
        FoobarCmdbCatalog catalog = new FoobarCmdbCatalog(foobarRepo("foobarcmdb", ""), client);

        CmdbItemsResponse response = catalog.list();

        assertEquals(List.of(), response.getItems());
        verifyNoInteractions(client);
    }

    @Test
    void readsFoobarcmdbOnly() {
        CmdbGithubClient client = mock(CmdbGithubClient.class);
        when(client.fetchFile(any(CmdbProperties.Github.class), eq("cmdb/cis.yaml"))).thenReturn(new CmdbGithubFile("""
            items:
              - name: Widget
                category: SYS
                ci_number: FOOBAR-SYS-01
                status: active
            """, "sha-foobar", "https://github.com/slingshot-aerospace-inc/foobarcmdb/blob/main/cmdb/cis.yaml"));

        FoobarCmdbProperties foobar = foobarRepo("foobarcmdb", "foobar-token");
        foobar.setCacheTtlSeconds(60);
        FoobarCmdbCatalog catalog = new FoobarCmdbCatalog(foobar, client);

        CmdbItemsResponse first = catalog.list();
        CmdbItemsResponse second = catalog.list();

        assertEquals("FOOBAR-SYS-01", first.getItems().get(0).getCiNumber());
        assertEquals(first, second);
        verify(client, times(1)).fetchFile(
            org.mockito.ArgumentMatchers.<CmdbProperties.Github>argThat(github ->
                "foobarcmdb".equals(github.getRepo())
                    && "slingshot-aerospace-inc".equals(github.getOwner())),
            eq("cmdb/cis.yaml")
        );
    }

    private static FoobarCmdbProperties foobarRepo(String repo, String token) {
        FoobarCmdbProperties properties = new FoobarCmdbProperties();
        properties.getGithub().setOwner("slingshot-aerospace-inc");
        properties.getGithub().setRepo(repo);
        properties.getGithub().setBranch("main");
        properties.getGithub().setToken(token);
        properties.getIndex().setPath("cmdb/cis.yaml");
        return properties;
    }
}
