package com.redhat.cloud.notifications.processors.slack;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RULE_ID;

@QuarkusTest
public class SlackProcessorTest extends CamelQuarkusTestSupport {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    SlackProcessor slackProcessor;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    @EndpointInject("mock:slack")
    MockEndpoint mockEndpoint;

    /*@Override
    public String isMockEndpoints() {
        return "slack*";
    }*/

    @Test
    void test() throws InterruptedException {

        Action action = new Action.ActionBuilder()
                .withBundle("rhel")
                .withApplication("advisor")
                .withEventType("foo")
                .withOrgId(DEFAULT_ORG_ID)
                .withTimestamp(LocalDateTime.now())
                .withContext(new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", "6ad30f3e-0497-4e74-99f1-b3f9a6120a6f")
                        .withAdditionalProperty("display_name", "my-computer")
                        .withAdditionalProperty("tags", List.of())
                        .build()
                )
                .withEvents(List.of(new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(new Payload.PayloadBuilder()
                                /* Supplied data */
                                .withAdditionalProperty(RULE_ID, "fuleid")
                                .build()
                        ).build()))
                .build();

        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setAction(action);

        CamelProperties camelProperties = new CamelProperties();
        camelProperties.setUrl("http://foo.com");
        camelProperties.setExtras(Map.of("channel", "#gwenneg-test"));

        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.CAMEL);
        endpoint.setOrgId("org_id");
        endpoint.setSubType("slack");
        endpoint.setProperties(camelProperties);

        statelessSessionFactory.withSession(statelessSession -> {
            slackProcessor.process(event, List.of(endpoint));
        });

        mockEndpoint.expectedBodiesReceived("coucou");

        mockEndpoint.assertIsSatisfied();
    }
}
