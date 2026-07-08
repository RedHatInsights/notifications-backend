package com.redhat.cloud.notifications.recipients.rest;

import com.redhat.cloud.notifications.recipients.model.User;
import com.redhat.cloud.notifications.recipients.resolver.FetchUsersFromExternalServices;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Reproducer for a regression introduced by the Quarkus 3.37 upgrade: the "reflection-free"
 * Jackson (de)serializers fail to populate {@code RecipientSettings.users} (a private field
 * exposed only through a getter, with no setter) when the request comes in as real JSON over
 * HTTP. As a result, the "users" filter is silently ignored and the full org user list is
 * returned instead of just the requested users.
 *
 * Unlike {@link com.redhat.cloud.notifications.recipients.resolver.RecipientsResolverTest},
 * which builds {@code RecipientSettings} instances directly in Java and therefore never
 * exercises Jackson deserialization, this test sends the raw JSON payload through the real
 * "/internal/recipients-resolver" endpoint.
 */
@QuarkusTest
public class RecipientResolverResourceUsersFilterTest {

    private static final String ORG_ID = "12341234";

    @InjectMock
    FetchUsersFromExternalServices fetchUsersFromExternalServices;

    @Test
    void testUsersFilterIsAppliedWhenDeserializedFromJson() {
        User user1 = createUser("userId1", "user-1");
        User user2 = createUser("userId2", "user-2");
        User user3 = createUser("userId3", "user-3");

        when(fetchUsersFromExternalServices.getUsers(eq(ORG_ID), eq(false)))
                .thenReturn(List.of(user1, user2, user3));

        String requestBody = "{"
                + "\"org_id\": \"" + ORG_ID + "\","
                + "\"recipient_settings\": [{\"admins_only\": false, \"ignore_user_preferences\": false, \"users\": [\"user-1\"]}],"
                + "\"subscribed_by_default\": true"
                + "}";

        List<User> recipients = given()
                .when()
                .contentType(JSON)
                .body(requestBody)
                .put("/internal/recipients-resolver")
                .then()
                .statusCode(200)
                .extract().response().as(new TypeRef<>() { });

        // Only "user-1" was requested via the "users" filter: the response must not include
        // "user-2" or "user-3", even though they belong to the same org and are subscribed by default.
        assertEquals(Set.of(user1), Set.copyOf(recipients));
    }

    private static User createUser(String userId, String username) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        return user;
    }
}
