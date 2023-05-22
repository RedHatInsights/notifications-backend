package com.redhat.cloud.notifications.processors.camel.google.chat;

import com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessorTest;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Processor;
import javax.inject.Inject;

@QuarkusTest
public class GoogleChatNotificationProcessorTest extends CamelNotificationProcessorTest {

    @Inject
    GoogleChatNotificationProcessor googleChatNotificationProcessor;

    @Override
    protected Processor getProcessor() {
        return googleChatNotificationProcessor;
    }

}
