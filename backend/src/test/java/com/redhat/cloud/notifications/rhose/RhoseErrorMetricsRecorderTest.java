package com.redhat.cloud.notifications.rhose;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.openbridge.RhoseErrorMetricsRecorder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServiceUnavailableException;

import static com.redhat.cloud.notifications.openbridge.RhoseErrorMetricsRecorder.COUNTER_NAME;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class RhoseErrorMetricsRecorderTest {

    @Inject
    RhoseErrorMetricsRecorder rhoseErrorMetricsRecorder;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(COUNTER_NAME, "path", "statusCode");
    }

    @Test
    void testCountersIncrement() {
        rhoseErrorMetricsRecorder.record("foo", new InternalServerErrorException());
        rhoseErrorMetricsRecorder.record("foo", new ServiceUnavailableException());
        rhoseErrorMetricsRecorder.record("foo", new InternalServerErrorException());

        rhoseErrorMetricsRecorder.record("bar", new ForbiddenException());
        rhoseErrorMetricsRecorder.record("bar", new NotFoundException());
        rhoseErrorMetricsRecorder.record("bar", new ServiceUnavailableException());
        rhoseErrorMetricsRecorder.record("bar", new InternalServerErrorException());

        micrometerAssertionHelper.assertCounterIncrement(COUNTER_NAME, 2L, "path", "foo", "statusCode", "500");
        micrometerAssertionHelper.assertCounterIncrement(COUNTER_NAME, 1L, "path", "foo", "statusCode", "503");

        micrometerAssertionHelper.assertCounterIncrement(COUNTER_NAME, 0L, "path", "bar", "statusCode", "403");
        micrometerAssertionHelper.assertCounterIncrement(COUNTER_NAME, 0L, "path", "bar", "statusCode", "404");
        micrometerAssertionHelper.assertCounterIncrement(COUNTER_NAME, 1L, "path", "bar", "statusCode", "500");
        micrometerAssertionHelper.assertCounterIncrement(COUNTER_NAME, 1L, "path", "bar", "statusCode", "503");
    }
}
