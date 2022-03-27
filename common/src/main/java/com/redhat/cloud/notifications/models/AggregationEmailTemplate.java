package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.EmailSubscriptionTypeConverter;
import com.redhat.cloud.notifications.models.filter.ApiResponseFilter;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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
@Table(name = "aggregation_email_template")
@JsonNaming(SnakeCaseStrategy.class)
@JsonFilter(ApiResponseFilter.NAME)
public class AggregationEmailTemplate extends CreationUpdateTimestamped {

    @Id
    @GeneratedValue
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private Application application;

    @Transient
    private UUID applicationId;

    @NotNull
    @Convert(converter = EmailSubscriptionTypeConverter.class)
    private EmailSubscriptionType subscriptionType;

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

    private boolean enabled;

    @Transient
    @JsonIgnore
    private boolean filterOutApplication;

    @Transient
    @JsonIgnore
    private boolean filterOutTemplates;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public UUID getApplicationId() {
        if (applicationId == null && application != null) {
            applicationId = application.getId();
        }
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public EmailSubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(EmailSubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFilterOutApplication() {
        return filterOutApplication;
    }

    public AggregationEmailTemplate filterOutApplication() {
        filterOutApplication = true;
        return this;
    }

    public boolean isFilterOutTemplates() {
        return filterOutTemplates;
    }

    public AggregationEmailTemplate filterOutTemplates() {
        filterOutTemplates = true;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof AggregationEmailTemplate) {
            AggregationEmailTemplate other = (AggregationEmailTemplate) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
