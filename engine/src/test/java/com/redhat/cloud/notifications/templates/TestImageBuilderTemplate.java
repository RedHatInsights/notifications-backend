package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.aggregators.ImageBuilderAggregator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestImageBuilderTemplate extends EmailTemplatesInDbHelper {

    static final String LAUNCH_SUCCESS = "launch-success";
    static final String LAUNCH_FAILURE = "launch-failed";

    private static final Action SUCCESS_ACTION = TestHelpers.createImageBuilderAction(LAUNCH_SUCCESS);
    private static final Action FAILURE_ACTION = TestHelpers.createImageBuilderAction(LAUNCH_FAILURE);

    @Override
    protected String getBundle() {
        return "rhel";
    }

    @Override
    protected String getApp() {
        return "image-builder";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(LAUNCH_SUCCESS, LAUNCH_FAILURE);
    }

    @Test
    public void testSuccessLaunchEmailTitle() {
        String result = generateEmailSubject(LAUNCH_SUCCESS, SUCCESS_ACTION);
        assertEquals("Instant notification - successful image launch - Image builder - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testSuccessLaunchEmailBody() {
        String result = generateEmailBody(LAUNCH_SUCCESS, SUCCESS_ACTION);
        assertTrue(result.contains("Instances launched successfully"));
        assertTrue(result.contains("91.123.32.4"));
    }

    @Test
    public void testFailedLaunchEmailTitle() {
        String result = generateEmailSubject(LAUNCH_FAILURE, FAILURE_ACTION);
        assertEquals("Instant notification - image launch failed - Image builder - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testFailedLaunchEmailBody() {
        String result = generateEmailBody(LAUNCH_FAILURE, FAILURE_ACTION);
        assertTrue(result.contains("An image failed to launch"));
        assertTrue(result.contains("Some launch error"));
    }

    @Test
        void testDailyTemplate() {
        ImageBuilderAggregator aggregator = new ImageBuilderAggregator();
        aggregator.aggregate(TestHelpers.createImageBuilderAggregation(LAUNCH_SUCCESS));
        aggregator.aggregate(TestHelpers.createImageBuilderAggregation(LAUNCH_SUCCESS));
        aggregator.aggregate(TestHelpers.createImageBuilderAggregation(LAUNCH_FAILURE));

        String resultSubject = generateAggregatedEmailSubject(aggregator.getContext());
        assertEquals("Daily digest - Image builder - Red Hat Enterprise Linux", resultSubject);

        String resultBody = generateAggregatedEmailBody(aggregator.getContext());
        assertTrue(resultBody.contains("2 launch attempts deployed"));
        assertTrue(resultBody.contains("1 launch attempts failed"));
        assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
