package com.redhat.cloud.notifications.models;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "instant_email_template")
public class InstantEmailTemplate extends CreationUpdateTimestamped {

    /*
     * Because of the @MapsId annotation on the `eventType` field, an InstantEmailTemplate instance and its parent
     * EventType instance will share the same @Id value. As a consequence, the `id` field doesn't need to be generated.
     */
    @Id
    private UUID id;

    @MapsId
    @ManyToOne
    @JoinColumn(name = "event_type_id")
    @NotNull
    private EventType eventType;

    @ManyToOne
    @JoinColumn(name = "subject_template_id")
    @NotNull
    private Template subjectTemplate;

    @ManyToOne
    @JoinColumn(name = "body_template_id")
    @NotNull
    private Template bodyTemplate;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Template getSubjectTemplate() {
        return subjectTemplate;
    }

    public void setSubjectTemplate(Template subjectTemplate) {
        this.subjectTemplate = subjectTemplate;
    }

    public Template getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(Template bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof InstantEmailTemplate) {
            InstantEmailTemplate other = (InstantEmailTemplate) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
