package com.redhat.cloud.notifications.templates.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnvironmentTest {

    @Test
    public void url() {
        Environment environment = new Environment();

        environment.environment = "prod";
        assertEquals("https://console.redhat.com/foobar", environment.url("/foobar"));
        // Adds slash if path does not start with it
        assertEquals("https://console.redhat.com/foobar", environment.url("foobar"));
        // No slash is added to base
        assertEquals("https://console.redhat.com", environment.url());
        assertEquals("prod", environment.name());

        environment.environment = "stage";
        assertEquals("https://console.stage.redhat.com/blabla/etc", environment.url("/blabla/etc"));
        assertEquals("stage", environment.name());

        environment.environment = "ephemeral";
        assertEquals("/nothing-to-see-here", environment.url("/nothing-to-see-here"));
        assertEquals("ephemeral", environment.name());

        environment.environment = "anything-else";
        assertEquals("/anything-else", environment.url("/anything-else"));
        assertEquals("/anything-else", environment.url("anything-else"));
        assertEquals("anything-else", environment.name());
    }
}
