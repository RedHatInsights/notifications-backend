package com.redhat.cloud.notifications.processors.payload;

import com.redhat.cloud.notifications.models.CreationTimestamped;
import com.redhat.cloud.notifications.models.Event;
import io.vertx.core.json.JsonObject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity(name = "PayloadDetails")
@Table(name = "payload_details")
public class PayloadDetails extends CreationTimestamped {

    public static final String X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER = "x-rh-notifications-payload-id";
    @Column(name = "event_id")
    @Id
    private UUID eventId;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "contents", nullable = false)
    private String contents;

    public PayloadDetails() {

    }

    public PayloadDetails(final Event event, final JsonObject contents) {
        this.eventId = event.getId();
        this.orgId = event.getOrgId();
        this.contents = contents.encode();
    }

    public PayloadDetails(final Event event, final String contents) {
        this.eventId = event.getId();
        this.orgId = event.getOrgId();
        this.contents = contents;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(final String orgId) {
        this.orgId = orgId;
    }

    public String getContents() {
        return contents;
    }

    public void setContents(final String payload) {
        this.contents = payload;
    }
}
