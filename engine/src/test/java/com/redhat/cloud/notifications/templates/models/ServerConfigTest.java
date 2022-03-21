package com.redhat.cloud.notifications.templates.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerConfigTest {

    @Test
    public void url() {
        ServerConfig config = new ServerConfig();

        config.environment = "prod";
        assertEquals("https://console.redhat.com/foobar", config.url("/foobar"));

        config.environment = "stage";
        assertEquals("https://console.stage.redhat.com/blabla/etc", config.url("/blabla/etc"));

        config.environment = "ephemeral";
        assertEquals("/nothing-to-see-here", config.url("/nothing-to-see-here"));

        config.environment = "anything-else";
        assertEquals("/anything-else", config.url("/anything-else"));
    }
}
