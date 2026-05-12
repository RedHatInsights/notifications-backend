package com.redhat.cloud.notifications.connector.email.model.aggregation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnvironmentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserializationFromCamelCaseKeys() throws Exception {
        String json = """
            {
                "url": "https://console.redhat.com",
                "ocmUrl": "https://ocm.redhat.com",
                "applicationServicesUrl": "https://app-services.redhat.com/"
            }
            """;

        Environment env = objectMapper.readValue(json, Environment.class);

        assertEquals("https://console.redhat.com", env.url());
        assertEquals("https://ocm.redhat.com", env.ocmUrl());
        assertEquals("https://app-services.redhat.com/", env.applicationServicesUrl());
    }
}
