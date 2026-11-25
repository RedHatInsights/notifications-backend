package com.redhat.cloud.notifications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.recipients.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The aggregator's {@link User} only keeps the username and the email. The recipients-resolver answers with more
 * properties, which must be ignored by the application's JSON mapping instead of failing the call.
 */
@QuarkusTest
class RecipientsResolverResponseMappingTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void propertiesTheAggregatorDoesNotKeepAreIgnored() throws Exception {
        String response = "[{\"id\":\"1\",\"username\":\"alice\",\"email\":\"alice@example.com\",\"firstName\":\"Alice\","
            + "\"lastName\":\"Doe\",\"active\":true,\"admin\":false,\"is_admin\":false,\"somethingNew\":{\"a\":1}}]";

        Set<User> users = objectMapper.readValue(response, new TypeReference<Set<User>>() { });

        assertEquals(1, users.size());
        User user = users.iterator().next();
        assertEquals("alice", user.getUsername());
        assertEquals("alice@example.com", user.getEmail());
    }
}
