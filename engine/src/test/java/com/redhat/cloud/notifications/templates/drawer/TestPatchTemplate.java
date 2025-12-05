package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.PatchTestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestPatchTemplate extends IntegrationTemplatesInDbHelper {


    static final String NEW_ADVISORY = "new-advisory";

    private static final Action ACTION = PatchTestHelpers.createPatchAction();

    @Override
    protected String getApp() {
        return "patch";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(NEW_ADVISORY);
    }

    @Test
    void testRenderedTemplateNewAdvisory() {
        String result = generateDrawerTemplate(NEW_ADVISORY, ACTION);
        assertEquals("Red Hat Insights has just released new Advisories for your organization. Please review the systems affected and all the details of each errata.", result);
    }
}
