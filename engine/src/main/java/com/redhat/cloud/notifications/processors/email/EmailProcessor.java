package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.templates.TemplateService;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class EmailProcessor extends SystemEndpointTypeProcessor {
    @Inject
    ConnectorSender connectorSender;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EmailSubscriptionTypeProcessor emailSubscriptionTypeProcessor;

    @Inject
    TemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Override
    public void process(final Event event, final List<Endpoint> endpoints) {

        // Generate an aggregation if the event supports it.
        this.emailSubscriptionTypeProcessor.generateAggregationWhereDue(event);

        // Fetch the template that will be used to hydrate it with the data.
        final Optional<InstantEmailTemplate> instantEmailTemplateMaybe = this.templateRepository.findInstantEmailTemplate(event.getEventType().getId());
        if (instantEmailTemplateMaybe.isEmpty()) {
            Log.debugf("[event_uuid: %s] The event was skipped because there were no suitable templates for it", event.getId());
            return;
        }

        final Set<RecipientSettings> recipientSettings = this.extractAndTransformRecipientSettings(event, endpoints);

        // Render the subject and the body of the email.
        final InstantEmailTemplate instantEmailTemplate = instantEmailTemplateMaybe.get();

        final String subjectData = instantEmailTemplate.getSubjectTemplate().getData();
        final String bodyData = instantEmailTemplate.getBodyTemplate().getData();

        final TemplateInstance subjectTemplate = this.templateService.compileTemplate(subjectData, "subject");
        final TemplateInstance bodyTemplate = this.templateService.compileTemplate(bodyData, "body");

        final String subject = this.templateService.renderTemplate(event.getEventWrapper().getEvent(), subjectTemplate);
        final String body = this.templateService.renderTemplate(event.getEventWrapper().getEvent(), bodyTemplate);

        // Prepare all the data to be sent to the connector.
        final EmailNotification emailNotification = new EmailNotification(
            body,
            subject,
            event.getOrgId(),
            event.getEventType().getId(),
            SubscriptionType.INSTANT.name(),
            recipientSettings
        );

        final JsonObject payload = JsonObject.mapFrom(emailNotification);

        final Endpoint endpoint = this.endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION);

        this.connectorSender.send(event, endpoint, payload);
    }
}
