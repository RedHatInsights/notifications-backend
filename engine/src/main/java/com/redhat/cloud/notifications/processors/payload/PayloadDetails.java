package com.redhat.cloud.notifications.processors.payload;

import com.redhat.cloud.notifications.models.CreationTimestamped;
import io.vertx.core.json.JsonObject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity(name = "PayloadDetails")
@Table(name = "payload_details")
public class PayloadDetails extends CreationTimestamped {

    public static final String X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER = "x-rh-notifications-payload-id";

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "contents", nullable = false)
    private String contents;

    public PayloadDetails() {

    }

    public PayloadDetails(final String orgId, final JsonObject contents) {
        this.orgId = orgId;
        this.contents = contents.encode();
    }

    public PayloadDetails(final String orgId, final String contents) {
        this.orgId = orgId;
        this.contents = contents;
    }

    public UUID getId() {
        return id;
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
