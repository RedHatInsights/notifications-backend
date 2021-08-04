package com.redhat.cloud.notifications.oapi;

import com.redhat.cloud.notifications.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OApiFilterTest {

    private final OApiFilter testee = new OApiFilter();

    @Test
    void shouldReturnNullWhenInputDoesNotStartWithUrlConstant() {
        Assertions.assertNull(testee.mangle("/someUrlStuffWithoutStartingWithUrlConstant"));
    }

    @Test
    void shouldReturnSlashWhenInputIsConstantOnly() {
        final String slash = testee.mangle(Constants.API_INTEGRATIONS_V_1_0);
        Assertions.assertEquals("/", slash);
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.API_NOTIFICATIONS_V_1_0, Constants.API_INTEGRATIONS_V_1_0, Constants.INTERNAL})
    void shouldReturnEverythingAfterConstant(String urlPath) {
        final String slash = testee.mangle(urlPath + "/someUrlAdditions");
        Assertions.assertEquals("/someUrlAdditions", slash);
    }
}