package com.redhat.cloud.notifications.oapi;

import com.redhat.cloud.notifications.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
