package com.releasedash.cmdb;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CmdbWriteServiceTest {

    @Test
    void createOpensPullRequest() {
        CmdbGithubClient github = mock(CmdbGithubClient.class);
        CmdbCatalogService catalog = mock(CmdbCatalogService.class);
        when(catalog.list(null, null)).thenReturn(new CmdbItemsResponse(List.of(), Instant.now(), "sha", null));
        when(github.fileExists("systems/MENTAT-SYS-03.yaml")).thenReturn(false);
        when(github.defaultBranch()).thenReturn("main");
        when(github.defaultBranchSha()).thenReturn("abc123");
        CmdbWriteResult pr = new CmdbWriteResult();
        pr.setPrUrl("https://github.com/slingshot-aerospace-inc/mentatcmdb/pull/12");
        pr.setPrNumber(12);
        when(github.createPullRequest(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq("cmdb/MENTAT-SYS-03-release-dashboard"),
            org.mockito.ArgumentMatchers.eq("main")
        )).thenReturn(pr);

        CmdbWriteService service = new CmdbWriteService(new CmdbProperties(), catalog, github);
        CmdbItem item = new CmdbItem();
        item.setName("Release Dashboard");
        item.setCategory("SYS");
        item.setCiNumber("MENTAT-SYS-03");
        item.setParent(null);
        item.setStatus("awaiting approval");

        CmdbWriteResult result = service.create(item);
        assertEquals("https://github.com/slingshot-aerospace-inc/mentatcmdb/pull/12", result.getPrUrl());
        assertEquals("systems/MENTAT-SYS-03.yaml", result.getPath());
        assertEquals("pr", result.getMode());
        verify(github).createBranch("cmdb/MENTAT-SYS-03-release-dashboard", "abc123");
        verify(github).putFile(
            org.mockito.ArgumentMatchers.eq("systems/MENTAT-SYS-03.yaml"),
            org.mockito.ArgumentMatchers.contains("MENTAT-SYS-03"),
            org.mockito.ArgumentMatchers.eq("chore(cmdb): add MENTAT-SYS-03"),
            org.mockito.ArgumentMatchers.eq("cmdb/MENTAT-SYS-03-release-dashboard")
        );
    }

    @Test
    void createRejectsDuplicateCiNumber() {
        CmdbGithubClient github = mock(CmdbGithubClient.class);
        CmdbCatalogService catalog = mock(CmdbCatalogService.class);
        CmdbItem existing = new CmdbItem();
        existing.setCiNumber("MENTAT-SYS-03");
        when(catalog.list(null, null)).thenReturn(new CmdbItemsResponse(List.of(existing), Instant.now(), "sha", null));

        CmdbWriteService service = new CmdbWriteService(new CmdbProperties(), catalog, github);
        CmdbItem item = new CmdbItem();
        item.setName("Release Dashboard");
        item.setCategory("SYS");
        item.setCiNumber("MENTAT-SYS-03");
        item.setStatus("awaiting approval");

        CmdbConflictException error = assertThrows(CmdbConflictException.class, () -> service.create(item));
        assertTrue(error.getMessage().contains("MENTAT-SYS-03"));
    }

    @Test
    void branchNameSanitizes() {
        assertEquals("cmdb/MENTAT-SYS-03-release-dashboard", CmdbWriteService.branchName("MENTAT-SYS-03", "Release Dashboard"));
        assertEquals("cmdb/MENTAT-SYS-01-A", CmdbWriteService.branchName("MENTAT-SYS-01.A", " "));
        assertEquals("cmdb/MENTAT-SYS-01-A-01", CmdbWriteService.branchName("MENTAT-SYS-01.A.01", " "));
    }
}
