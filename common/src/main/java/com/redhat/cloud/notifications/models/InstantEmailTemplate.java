package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.filter.ApiResponseFilter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import static javax.persistence.FetchType.LAZY;

// TODO NOTIF-484 Add templates ownership and restrict access to the templates.
@Entity
@Table(name = "instant_email_template")
@JsonNaming(SnakeCaseStrategy.class)
@JsonFilter(ApiResponseFilter.NAME)
public class InstantEmailTemplate extends CreationUpdateTimestamped {

    @Id
    @GeneratedValue
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    @Transient
    private UUID eventTypeId;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "subject_template_id")
    @JsonProperty(access = READ_ONLY)
    private Template subjectTemplate;

    @NotNull
    @Transient
    private UUID subjectTemplateId;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "body_template_id")
    @JsonProperty(access = READ_ONLY)
    private Template bodyTemplate;

    @NotNull
    @Transient
    private UUID bodyTemplateId;

    @Transient
    @JsonIgnore
    private boolean filterOutEventType;

    @Transient
    @JsonIgnore
    private boolean filterOutTemplates;

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

    public UUID getEventTypeId() {
        if (eventTypeId == null && eventType != null) {
            eventTypeId = eventType.getId();
        }
        return eventTypeId;
    }

    public void setEventTypeId(UUID eventTypeId) {
        this.eventTypeId = eventTypeId;
    }

    public Template getSubjectTemplate() {
        return subjectTemplate;
    }

    public void setSubjectTemplate(Template subjectTemplate) {
        this.subjectTemplate = subjectTemplate;
    }

    public UUID getSubjectTemplateId() {
        if (subjectTemplateId == null && subjectTemplate != null) {
            subjectTemplateId = subjectTemplate.getId();
        }
        return subjectTemplateId;
    }

    public void setSubjectTemplateId(UUID subjectTemplateId) {
        this.subjectTemplateId = subjectTemplateId;
    }

    public Template getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(Template bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }

    public UUID getBodyTemplateId() {
        if (bodyTemplateId == null && bodyTemplate != null) {
            bodyTemplateId = bodyTemplate.getId();
        }
        return bodyTemplateId;
    }

    public void setBodyTemplateId(UUID bodyTemplateId) {
        this.bodyTemplateId = bodyTemplateId;
    }

    public boolean isFilterOutEventType() {
        return filterOutEventType;
    }

    public InstantEmailTemplate filterOutEventType() {
        filterOutEventType = true;
        return this;
    }

    public boolean isFilterOutTemplates() {
        return filterOutTemplates;
    }

    public InstantEmailTemplate filterOutTemplates() {
        filterOutTemplates = true;
        return this;
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
