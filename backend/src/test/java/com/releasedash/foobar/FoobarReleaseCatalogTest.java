package com.releasedash.foobar;

import com.releasedash.dto.ReleaseResponse;
import com.releasedash.dto.StatsResponse;
import com.releasedash.service.JfrogLocation;
import com.releasedash.service.ReleaseService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FoobarReleaseCatalogTest {

    @Test
    void blankFolderDoesNotCallJfrog() {
        ReleaseService releases = mock(ReleaseService.class);
        FoobarJfrogProperties properties = new FoobarJfrogProperties();
        properties.setRepoKey("ssa-generic");

        FoobarReleaseCatalog catalog = new FoobarReleaseCatalog(properties, releases);

        assertEquals(0L, catalog.stats().getTotalReleases());
        assertEquals(List.of(), catalog.list(null, null, 50, 0));
        assertTrue(catalog.release("widget", 1, "preview").isEmpty());
        verifyNoInteractions(releases);
    }

    @Test
    void configuredFolderIsPassedToTheReleaseService() {
        ReleaseService releases = mock(ReleaseService.class);
        JfrogLocation folder = new JfrogLocation("ssa-generic", "foobar-release-artifacts");
        when(releases.getReleases(folder, "widget", "preview", 50, 0)).thenReturn(List.of(new ReleaseResponse()));
        when(releases.getStats(folder)).thenReturn(new StatsResponse(1L, List.of("widget"), List.of("preview")));

        FoobarJfrogProperties properties = new FoobarJfrogProperties();
        properties.setRepoKey("ssa-generic");
        properties.setPrefix("foobar-release-artifacts");
        FoobarReleaseCatalog catalog = new FoobarReleaseCatalog(properties, releases);

        assertEquals(1L, catalog.stats().getTotalReleases());
        assertEquals(1, catalog.list("widget", "preview", 50, 0).size());
        verify(releases).getStats(folder);
        verify(releases).getReleases(folder, "widget", "preview", 50, 0);
    }
}
