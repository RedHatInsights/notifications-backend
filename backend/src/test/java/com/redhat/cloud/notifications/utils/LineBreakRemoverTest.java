package com.redhat.cloud.notifications.utils;

import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.utils.LineBreakCleaner.clean;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LineBreakRemoverTest {

    @Test
    void testCr() {
        String initialValue = "Hello,\r world\r";
        assertTrue(initialValue.contains("\r"));
        String cleanedValue = clean(initialValue);
        assertFalse(cleanedValue.contains("\r"));
    }

    @Test
    void testLf() {
        String initialValue = "Hello,\n world\n";
        assertTrue(initialValue.contains("\n"));
        String cleanedValue = clean(initialValue);
        assertFalse(cleanedValue.contains("\n"));
    }

    @Test
    void testCrLf() {
        String initialValue = "Hello,\r\n world\r\n";
        assertTrue(initialValue.contains("\r"));
        assertTrue(initialValue.contains("\n"));
        String cleanedValue = clean(initialValue);
        assertFalse(cleanedValue.contains("\r"));
        assertFalse(cleanedValue.contains("\n"));
    }
}
