package com.redhat.cloud.notifications.routers.handlers.event;

import jakarta.ws.rs.Path;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_2_0;

@Path(API_NOTIFICATIONS_V_2_0 + "/notifications/events")
public class EventResourceV2 extends EventResource {
}
