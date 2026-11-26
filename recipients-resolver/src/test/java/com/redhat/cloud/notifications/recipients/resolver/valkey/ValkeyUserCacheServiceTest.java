package com.redhat.cloud.notifications.recipients.resolver.valkey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValkeyUserCacheServiceTest {

    @Test
    void testFormatKey() {
        assertEquals("recipients:users:org123:true", ValkeyUserCacheService.formatKey("org123", true));
        assertEquals("recipients:users:org123:false", ValkeyUserCacheService.formatKey("org123", false));
        assertEquals("recipients:users:my-org-id:true", ValkeyUserCacheService.formatKey("my-org-id", true));
    }
}
