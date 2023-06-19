package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.Query;
import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestHelpers {

    public static String encodeRHIdentityInfo(String accountId, String orgId, String username) {
        JsonObject identity = new JsonObject();
        JsonObject user = new JsonObject();
        user.put("username", username);
        identity.put("account_number", accountId);
        identity.put("org_id", orgId);
        identity.put("user", user);
        identity.put("type", "User");
        JsonObject header = new JsonObject();
        header.put("identity", identity);

        return Base64Utils.encode(header.encode());
    }

    public static String encodeTurnpikeIdentityInfo(String username, String... groups) {
        JsonObject identity = new JsonObject();
        JsonObject associate = new JsonObject();
        JsonArray roles = new JsonArray();

        identity.put("auth_type", "saml-auth");
        identity.put("type", "Associate");
        identity.put("associate", associate);
        associate.put("email", username);
        associate.put("Role", roles);
        for (String group: groups) {
            roles.add(group);
        }

        JsonObject header = new JsonObject();
        header.put("identity", identity);

        return Base64Utils.encode(header.encode());
    }

    public static Header createTurnpikeIdentityHeader(String username, String... roles) {
        return new Header(X_RH_IDENTITY_HEADER, encodeTurnpikeIdentityInfo(username, roles));
    }

    public static Header createRHIdentityHeader(String encodedIdentityHeader) {
        return new Header(X_RH_IDENTITY_HEADER, encodedIdentityHeader);
    }

    /**
     * Utility function to test sorting using the Query class
     *
     * @param sortField Field to sortBy
     * @param producer Function that takes a query and returns the sorted list of elements
     * @param testAdapter Function to transform the sorted list of elements into the expected sorting values
     * @param defaultOrder Default order used when the order is not provided
     * @param expectedDefault Expected default values
     * @param <T> Element type being sorted
     * @param <P> Expected element type
     */
    public static <T, P> void testSorting(
            String sortField,
            Function<Query, List<T>> producer,
            Function<List<T>, List<P>> testAdapter,
            Query.Sort.Order defaultOrder,
            List<P> expectedDefault) {

        List<P> asc = new ArrayList<>(expectedDefault);
        List<P> desc = new ArrayList<>(expectedDefault);

        if (defaultOrder == Query.Sort.Order.ASC) {
            Collections.reverse(desc);
        } else {
            Collections.reverse(asc);
        }

        Query query = Query.queryWithSortBy(sortField);
        List<T> values = producer.apply(query);
        assertEquals(expectedDefault, testAdapter.apply(values));

        query = Query.queryWithSortBy(sortField + ":asc");
        values = producer.apply(query);
        assertEquals(asc, testAdapter.apply(values));

        query = Query.queryWithSortBy(sortField + ":desc");
        values = producer.apply(query);
        assertEquals(desc, testAdapter.apply(values));
    }

    /**
     * Extracts the constraint violation from the returned response.
     * @param response the response to extract the constraint violation from.
     * @return the extracted constraint violation message, or an invalid message instead, intended to make the
     * assertions fail.
     */
    public static String extractConstraintViolationFromResponse(final String response) {
        final var json = new JsonObject(response);

        final JsonArray constraintViolations = json.getJsonArray("violations");
        if (constraintViolations.size() != 1) {
            return String.format("one constraint violation expected, more were returned in the payload: %s", response);
        }

        final JsonObject arrayElement = constraintViolations.getJsonObject(0);

        return arrayElement.getString("message");
    }
}
