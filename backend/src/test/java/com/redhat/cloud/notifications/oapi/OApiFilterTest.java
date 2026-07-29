package com.redhat.cloud.notifications.oapi;

import com.redhat.cloud.notifications.Constants;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class OApiFilterTest {

    private final OApiFilter testee = new OApiFilter();

    @Test
    void shouldReturnNullWhenInputDoesNotStartWithUrlConstant() {
        Assertions.assertNull(testee.mangle("/someUrlStuffWithoutStartingWithUrlConstant", "integrations", "v1.0"));
    }

    @Test
    void shouldReturnSlashWhenInputIsConstantOnly() {
        final String slash = testee.mangle(Constants.API_INTEGRATIONS_V_1_0, "integrations", "v1.0");
        Assertions.assertEquals("/", slash);
    }

    @Test
    void shouldReturnEverythingAfterConstant() {
        String[][] testCases = {{Constants.API_NOTIFICATIONS_V_1_0, OApiFilter.NOTIFICATIONS, "v1.0"},
            {Constants.API_NOTIFICATIONS_V_2_0, OApiFilter.NOTIFICATIONS, "v2.0"},
            {Constants.API_INTEGRATIONS_V_1_0, OApiFilter.INTEGRATIONS, "v1.0"},
            {Constants.API_INTEGRATIONS_V_2_0, OApiFilter.INTEGRATIONS, "v2.0"},
            {Constants.API_INTERNAL, OApiFilter.INTERNAL, null}};

        for (String[] testCase: testCases) {
            final String slash = testee.mangle(testCase[0] + "/someUrlAdditions", testCase[1], testCase[2]);
            Assertions.assertEquals("/someUrlAdditions", slash);
        }
    }

    @Test
    void shouldKeepSchemaRefFromArrayTypedRequestBody() {
        // A JAX-RS method taking a List<SomeDTO> produces a requestBody schema of
        // {"type": "array", "items": {"$ref": "..."}} rather than a top-level "$ref". Without the
        // dedicated array-items branch in findSchemas(), this ref would be silently dropped, and
        // the referenced schema would be missing from the generated per-domain OpenAPI document.
        JsonObject pathItem = new JsonObject()
            .put("put", new JsonObject()
                .put("operationId", "someOperation")
                .put("requestBody", new JsonObject()
                    .put("content", new JsonObject()
                        .put("application/json", new JsonObject()
                            .put("schema", new JsonObject()
                                .put("type", "array")
                                .put("items", new JsonObject()
                                    .put("$ref", "#/components/schemas/SomeDTO"))))))
                .put("responses", new JsonObject()));

        Set<String> schemasToKeep = testee.findSchemas(pathItem);

        Assertions.assertTrue(schemasToKeep.contains("SomeDTO"));
    }
}
