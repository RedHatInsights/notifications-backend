package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ResourceHelpers {

    public static final String TEST_APP_NAME = "Tester";
    public static final String TEST_EVENT_TYPE_FORMAT = "EventType%d";

    @Inject
    EndpointResources resources;

    @Inject
    ApplicationResources appResources;

    public void createTestAppAndEventTypes() {
        Application app = new Application();
        app.setName(TEST_APP_NAME);
        app.setDescription("...");
        Application added = appResources.createApplication(app).await().indefinitely();

        for(int i = 0; i < 100; i++) {
            EventType eventType = new EventType();
            eventType.setName(String.format(TEST_EVENT_TYPE_FORMAT, i));
            eventType.setDescription("... -> " + i);
            appResources.addEventTypeToApplication(added.getId(), eventType).await().indefinitely();
        }
    }
}
