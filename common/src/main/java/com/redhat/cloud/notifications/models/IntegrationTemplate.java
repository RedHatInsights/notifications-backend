package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;

/**
 * Integration templates are more generic templates directly targeted at Integrations
 * other than email, like Splunk or Slack via Camel and OpenBridge.
 * As integrations can be added quite dynamically, a discriminator
 * column #integrationType is used.
 *
 * Within integrations, we allow for a fallback mechanism, where a default
 * is used for all (sending) applications and users. And then more specific
 * templates can be set up. See #TemplateKind below.
 */
@Entity
@Table(name = "integration_template")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class IntegrationTemplate extends CreationUpdateTimestamped {

    @Id
    @GeneratedValue
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    // Name of the integration
    private String integrationType;

    // Kind of Template.
    private TemplateKind templateKind;

    private String accountId;

    @ManyToOne(optional = false)
    @JsonProperty(access = READ_ONLY)
    private Template theTemplate;

    public Template getTheTemplate() {
        return theTemplate;
    }

    public void setTheTemplate(Template theTemplate) {
        this.theTemplate = theTemplate;
    }

    public String getIntegrationType() {
        return integrationType;
    }

    public void setIntegrationType(String integrationType) {
        this.integrationType = integrationType;
    }

    public TemplateKind getTemplateKind() {
        return templateKind;
    }

    public void setTemplateKind(TemplateKind templateKind) {
        this.templateKind = templateKind;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    /*
     * Kind of a template. Ordering is from fallback/default to most specific.
     * The idea is that the caller can request a kind and if there is no
     * template available, the one of a less specific type is taken.
     */
    public enum TemplateKind {
        DEFAULT, // A fallback if nothing more specific is defined
        APPLICATION, // specific for one (sending) application
        EVENT_TYPE, // specific for an event_type of an application
        ORG  // defined for a single (customer) organisation
    }
}
