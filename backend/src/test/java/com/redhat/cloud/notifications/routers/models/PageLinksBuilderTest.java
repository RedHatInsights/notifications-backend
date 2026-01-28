package com.redhat.cloud.notifications.routers.models;

import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class PageLinksBuilderTest {

    @Test
    void testLimitHigherThanCount() {
        Map<String, String> links = PageLinksBuilder.build("test", 10, 15, 0);
        assertEquals("test?limit=15&offset=0", links.get("first"));
        assertEquals("test?limit=15&offset=0", links.get("last"));
        assertFalse(links.containsKey("prev"));
        assertFalse(links.containsKey("next"));
    }

    @Test
    void testLimitEqualsCount() {
        Map<String, String> links = PageLinksBuilder.build("test", 10, 10, 0);
        assertEquals("test?limit=10&offset=0", links.get("first"));
        assertEquals("test?limit=10&offset=0", links.get("last"));
        assertFalse(links.containsKey("prev"));
        assertFalse(links.containsKey("next"));
    }

    @Test
    void testLimitSmallerThanCountFirstPage() {
        Map<String, String> links = PageLinksBuilder.build("test", 10, 7, 0);
        assertEquals("test?limit=7&offset=0", links.get("first"));
        assertEquals("test?limit=7&offset=7", links.get("last"));
        assertEquals("test?limit=7&offset=7", links.get("next"));
        assertFalse(links.containsKey("prev"));
    }

    @Test
    void testLimitSmallerThanCountMiddlePage() {
        Map<String, String> links = PageLinksBuilder.build("test", 10, 3, 6);
        assertEquals("test?limit=3&offset=0", links.get("first"));
        assertEquals("test?limit=3&offset=9", links.get("last"));
        assertEquals("test?limit=3&offset=3", links.get("prev"));
        assertEquals("test?limit=3&offset=9", links.get("next"));
    }

    @Test
    void testLimitSmallerThanCountLastPage() {
        Map<String, String> links = PageLinksBuilder.build("test", 10, 3, 9);
        assertEquals("test?limit=3&offset=0", links.get("first"));
        assertEquals("test?limit=3&offset=9", links.get("last"));
        assertEquals("test?limit=3&offset=6", links.get("prev"));
        assertFalse(links.containsKey("next"));
    }

    @Test
    void testUriInfoPreservesQueryParameters() {
        UriInfo uriInfo = Mockito.mock(UriInfo.class);

        // Create a real UriBuilder from a URI with query parameters
        URI requestUri = URI.create("http://localhost/test?startDate=2024-01-01&limit=10&offset=0");
        UriBuilder uriBuilder = UriBuilder.fromUri(requestUri);

        when(uriInfo.getRequestUriBuilder()).thenReturn(uriBuilder);

        Map<String, String> links = PageLinksBuilder.build(uriInfo, 50, 10, 0);

        assertTrue(links.get("first").contains("startDate=2024-01-01"));
        assertTrue(links.get("next").contains("startDate=2024-01-01"));
        assertTrue(links.get("next").contains("offset=10"));
    }
}
