package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.notifications.ingress.Action;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BaseTransformerTest {

    private final BaseTransformer testee = new BaseTransformer();

    @Test
    void shouldContainOrgId() {
        Action action = new Action();
        action.setOrgId("someOrgId");
        action.setTimestamp(LocalDateTime.of(2022, 12, 24, 12, 0));

        final JsonObject jsonObject = testee.toJsonObject(action);

        assertEquals("someOrgId", jsonObject.getString("org_id"));
    }

    @Test
    void shouldNotRaiseAnExceptionWhenOrgIdIsMissing() {
        Action action = new Action();
        action.setTimestamp(LocalDateTime.of(2022, 12, 24, 12, 0));

        final JsonObject jsonObject = testee.toJsonObject(action);

        assertNull(jsonObject.getString("org_id"));
    }
}
