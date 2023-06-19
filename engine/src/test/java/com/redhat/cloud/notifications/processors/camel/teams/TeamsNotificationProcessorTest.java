package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessorTest;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Processor;
import javax.inject.Inject;

@QuarkusTest
public class TeamsNotificationProcessorTest extends CamelNotificationProcessorTest {

    @Inject
    TeamsNotificationProcessor teamsNotificationProcessor;

    @Override
    protected Processor getProcessor() {
        return teamsNotificationProcessor;
    }

}
