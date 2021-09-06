package com.redhat.cloud.notifications.routers.models;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
