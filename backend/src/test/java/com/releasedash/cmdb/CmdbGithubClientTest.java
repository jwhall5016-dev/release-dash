package com.releasedash.cmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CmdbGithubClientTest {

    @Test
    void contentsUriKeepsNestedPathSlashes() {
        URI uri = CmdbGithubClient.contentsUri(
            "slingshot-aerospace-inc",
            "mentatcmdb",
            "cmdb/cis.yaml",
            "main"
        );
        assertEquals(
            "https://api.github.com/repos/slingshot-aerospace-inc/mentatcmdb/contents/cmdb/cis.yaml?ref=main",
            uri.toString()
        );
    }

    @Test
    void teamFetchUsesTheGivenRepoNotTheMentatToken() {
        CmdbProperties properties = new CmdbProperties();
        properties.getGithub().setOwner("slingshot-aerospace-inc");
        properties.getGithub().setRepo("mentatcmdb");
        properties.getGithub().setToken("mentat-token");
        CmdbProperties.Github foobar = new CmdbProperties.Github();
        foobar.setOwner("slingshot-aerospace-inc");
        foobar.setRepo("foobarcmdb");

        CmdbGithubClient client = client(properties);
        CmdbGithubException error = assertThrows(
            CmdbGithubException.class,
            () -> client.fetchFile(foobar, "cmdb/cis.yaml")
        );
        assertEquals("CMDB GitHub token is not configured", error.getMessage());
    }

    @Test
    void missingTokenFailsBeforeGithub() {
        CmdbProperties properties = new CmdbProperties();
        properties.getGithub().setOwner("slingshot-aerospace-inc");
        properties.getGithub().setRepo("mentatcmdb");
        CmdbGithubClient client = client(properties);
        CmdbGithubException error = assertThrows(CmdbGithubException.class, () -> client.fetchFile("cmdb/cis.yaml"));
        assertEquals("CMDB GitHub token is not configured", error.getMessage());
    }

    private static CmdbGithubClient client(CmdbProperties properties) {
        ObjectMapper mapper = new ObjectMapper();
        return new CmdbGithubClient(properties, mapper, new CmdbGithubAppAuth(mapper));
    }
}
