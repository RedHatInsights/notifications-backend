package com.redhat.cloud.notifications.templates.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnvironmentTest {

    @Test
    public void url() {
        Environment environment = new Environment();

        environment.environment = "prod";
        assertEquals("https://console.redhat.com", environment.url());
        assertEquals("prod", environment.name());

        environment.environment = "stage";
        assertEquals("https://console.stage.redhat.com", environment.url());
        assertEquals("stage", environment.name());

        environment.environment = "ephemeral";
        assertEquals("/", environment.url());
        assertEquals("ephemeral", environment.name());

        environment.environment = "anything-else";
        assertEquals("/", environment.url());
        assertEquals("anything-else", environment.name());
    }
}
