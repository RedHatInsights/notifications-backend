package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.WebhookAttributes;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ResourceHelpers {

    public static final String TEST_APP_NAME = "Tester";
    public static final String TEST_APP_NAME_2 = "MyOtherTester";
    public static final String TEST_EVENT_TYPE_FORMAT = "EventType%d";

    @Inject
    EndpointResources resources;

    @Inject
    ApplicationResources appResources;

    public List<Application> getApplications() {
        return appResources.getApplications().collectItems().asList().await().indefinitely();
    }

    public void createTestAppAndEventTypes() {
        Application app = new Application();
        app.setName(TEST_APP_NAME);
        app.setDescription("...");
        Application added = appResources.createApplication(app).await().indefinitely();

        for (int i = 0; i < 100; i++) {
            EventType eventType = new EventType();
            eventType.setName(String.format(TEST_EVENT_TYPE_FORMAT, i));
            eventType.setDescription("... -> " + i);
            appResources.addEventTypeToApplication(added.getId(), eventType).await().indefinitely();
        }

        Application app2 = new Application();
        app2.setName(TEST_APP_NAME_2);
        app2.setDescription("...");
        Application added2 = appResources.createApplication(app2).await().indefinitely();

        for (int i = 0; i < 100; i++) {
            EventType eventType = new EventType();
            eventType.setName(String.format(TEST_EVENT_TYPE_FORMAT, i));
            eventType.setDescription("... -> " + i);
            appResources.addEventTypeToApplication(added2.getId(), eventType).await().indefinitely();
        }
    }

    public int[] createTestEndpoints(String tenant, int count) {
        int[] statsValues = new int[3];
        statsValues[0] = count;
        for (int i = 0; i < count; i++) {
            // Add new endpoints
            WebhookAttributes webAttr = new WebhookAttributes();
            webAttr.setMethod(WebhookAttributes.HttpType.POST);
            webAttr.setUrl("https://localhost");

            Endpoint ep = new Endpoint();
            if (i > 0) {
                ep.setType(Endpoint.EndpointType.WEBHOOK);
                ep.setName(String.format("Endpoint %d", count - i));
            } else {
                ep.setType(Endpoint.EndpointType.DEFAULT);
                ep.setName("Default endpoint");
            }
            ep.setDescription("Automatically generated");
            boolean enabled = (i % (count / 5)) != 0;
            if (!enabled) {
                statsValues[1]++;
            }
            ep.setEnabled(enabled);
            if (i > 0) {
                statsValues[2]++;
                ep.setProperties(webAttr);
            }

            ep.setTenant(tenant);
            resources.createEndpoint(ep).await().indefinitely();
        }
        return statsValues;
    }
}
