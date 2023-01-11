package com.redhat.cloud.notifications.utils;

import com.redhat.cloud.notifications.models.ConsoleCloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
public class CloudEventParserTest {

    @Inject
    CloudEventParser cloudEventParser;

    @Test
    public void shouldParseCorrectCloudEvents() throws IOException {
        InputStream advisorCloudEvent = CloudEventParserTest.class.getClassLoader().getResourceAsStream("cloud-events/advisor.json");
        assertNotNull(advisorCloudEvent, "could not load example cloud event for advisor");

        ConsoleCloudEvent consoleCloudEvent = cloudEventParser.fromJsonString(IOUtils.toString(advisorCloudEvent, UTF_8));

        assertEquals("com.redhat.console.advisor.new-recommendations", consoleCloudEvent.getType());
        assertEquals("org123", consoleCloudEvent.getOrgId());
        assertNull(consoleCloudEvent.getAccountId());
    }

    @Test
    public void shouldFailOnNonCompliantCloudEvents() throws IOException {
        InputStream advisorCloudEvent = CloudEventParserTest.class.getClassLoader().getResourceAsStream("cloud-events/advisor-invalid.json");
        assertNotNull(advisorCloudEvent, "could not load example cloud event for advisor");

        assertThrows(RuntimeException.class, () -> cloudEventParser.fromJsonString(IOUtils.toString(advisorCloudEvent, UTF_8)));
    }

    @Test
    public void shouldFailOnInvalidJson() throws IOException {
        assertThrows(RuntimeException.class, () -> cloudEventParser.fromJsonString("hello world"));
    }

}
