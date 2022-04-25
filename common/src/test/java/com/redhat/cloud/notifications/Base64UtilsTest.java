package com.redhat.cloud.notifications;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Base64UtilsTest {

    @Test
    void testEncodeDecode() {
        assertNull(Base64Utils.encode(null));
        assertNull(Base64Utils.decode(null));
        assertEquals("Hello, World!", Base64Utils.decode(Base64Utils.encode("Hello, World!")));
        assertEquals("éàïôñù", Base64Utils.decode(Base64Utils.encode("éàïôñù")));
    }
}
