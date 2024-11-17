package com.redhat.cloud.notifications.routers.handlers.event;

import jakarta.ws.rs.Path;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;

@Path(API_NOTIFICATIONS_V_1_0 + "/notifications/events")
public class EventResourceV1 extends EventResource {
}
