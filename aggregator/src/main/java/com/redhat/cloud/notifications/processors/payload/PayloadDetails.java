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

import java.util.Objects;
import java.util.UUID;

/**
 * Duplicate of engine's equivalent class (engine and aggregator are both deployable modules and can't
 * depend on each other), mapped to the same {@code payload_details} table in the Postgres DB both
 * modules share. Used to hand off a pre-folded digest payload too large to fit inline in the Kafka
 * message published to {@code ingress} -- engine reads it back via its own, unchanged
 * {@code PayloadDetailsRepository}.
 */
@Entity(name = "AggregatorPayloadDetails")
@Table(name = "payload_details")
public class PayloadDetails extends CreationTimestamped {

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

    /**
     * @param event an already-persisted event to anchor this row to for the {@code ON DELETE CASCADE}
     *              relationship. The specific event doesn't matter functionally -- {@code eventId} is
     *              never read back anywhere, only used for the cascade-delete lifecycle (see ticket
     *              1a-offload design notes) -- so any event already loaded as part of the fold works.
     */
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

    public void setContents(final String contents) {
        this.contents = contents;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PayloadDetails)) {
            return false;
        }
        PayloadDetails that = (PayloadDetails) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
