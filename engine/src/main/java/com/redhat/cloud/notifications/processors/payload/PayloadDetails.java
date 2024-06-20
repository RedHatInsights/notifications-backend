package com.redhat.cloud.notifications.processors.payload;

import com.redhat.cloud.notifications.models.CreationTimestamped;
import com.redhat.cloud.notifications.models.Event;
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
    /**
     * The key for the identifier of the payload which will go in the JSON
     * payload that we send over Kafka.
     */
    public static final String PAYLOAD_DETAILS_ID_KEY = "payload_details_id";

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "contents", nullable = false)
    private String contents;

    public PayloadDetails() {

    }

    public PayloadDetails(final Event event, final JsonObject contents) {
        this.eventId = event.getId();
        this.contents = contents.encode();
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getContents() {
        return contents;
    }

    public void setContents(final String payload) {
        this.contents = payload;
    }
}
