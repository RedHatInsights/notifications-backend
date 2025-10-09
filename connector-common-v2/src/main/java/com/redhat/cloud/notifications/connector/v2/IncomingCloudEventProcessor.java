package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.connector.v2.pojo.NotificationToConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.ENDPOINT_ID;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.START_TIME;

@ApplicationScoped
public class IncomingCloudEventProcessor {

    public static final String CLOUD_EVENT_ID = "id";
    public static final String CLOUD_EVENT_TYPE = "type";
    public static final String CLOUD_EVENT_DATA = "data";

    @Inject
    ConnectorConfig connectorConfig;

    public void process(MessageContext context) throws Exception {
        // This property will be used later to determine the invocation time.
        context.setProperty(START_TIME, System.currentTimeMillis());

        // Source of the Cloud Event returned to the Notifications engine.
        context.setProperty(RETURN_SOURCE, connectorConfig.getConnectorName());
        NotificationToConnector notificationToConnector = context.getTypedBody(NotificationToConnector.class);
        context.setProperty(ORG_ID, notificationToConnector.getOrgId());
        context.setProperty(ENDPOINT_ID, notificationToConnector.getEndpointId());
    }

}
