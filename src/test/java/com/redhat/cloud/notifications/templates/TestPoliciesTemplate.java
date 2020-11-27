package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.ingress.Tag;
import io.quarkus.test.junit.QuarkusTest;
import org.joda.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPoliciesTemplate {

    @Test
    public void testInstantEmailTitle() {
        List<Tag> tags = new ArrayList<Tag>();
        tags.add(Tag.newBuilder().setName("display_name").setValue("FooMachine").build());

        Map<String, String> triggers = new HashMap<>();
        triggers.put("abcd-efghi-jkl-lmn", "Foobar");
        triggers.put("0123-456-789-5721f", "Latest foo is installed");

        Map<String, Object> params = new HashMap<>();
        params.put("triggers", triggers);

        String result = Policies.Templates.instantEmailTitle()
                .data("params", params)
                .data("tags", tags)
                .data("timestamp", new LocalDateTime())
                .render();

        assertTrue(result.contains("2"), "Title contains the number of policies triggered");
        assertTrue(result.contains("FooMachine"), "Body should contain the display_name");
    }

    @Test
    public void testInstantEmailBody() {

        List<Tag> tags = new ArrayList<Tag>();
        tags.add(Tag.newBuilder().setName("display_name").setValue("FooMachine").build());

        Map<String, String> triggers = new HashMap<>();
        triggers.put("abcd-efghi-jkl-lmn", "Foobar");
        triggers.put("0123-456-789-5721f", "Latest foo is installed");

        Map<String, Object> params = new HashMap<>();
        params.put("triggers", triggers);

        String result = Policies.Templates.instantEmailBody()
                .data("params", params)
                .data("tags", tags)
                .data("timestamp", new LocalDateTime())
                .render();

        for (String key : triggers.keySet()) {
            String value = triggers.get(key);
            assertTrue(result.contains(key), "Body should contain trigger key '" + key + "'");
            assertTrue(result.contains(value), "Body should contain trigger value '" + value + "'");
        }

        // Display name
        assertTrue(result.contains("FooMachine"), "Body should contain the display_name");
    }
}
