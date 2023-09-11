package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.templates.TemplateService;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
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
        final Set<RecipientSettings> recipientSettings = this.extractAndTransformRecipientSettings(event, endpoints);

        // Generate an aggregation if the event supports it.
        this.emailSubscriptionTypeProcessor.generateAggregationWhereDue(event);

        // Fetch the template that will be used to hydrate it with the data.
        final Optional<InstantEmailTemplate> instantEmailTemplateMaybe = this.templateRepository.findInstantEmailTemplate(event.getEventType().getId());
        if (instantEmailTemplateMaybe.isEmpty()) {
            Log.debugf("[event_uuid: %s] The event was skipped because there were no suitable templates for it", event.getId());
            return;
        }

        // Render the subject and the body of the email.
        final InstantEmailTemplate instantEmailTemplate = instantEmailTemplateMaybe.get();

        final String subjectData = instantEmailTemplate.getSubjectTemplate().getData();
        final String bodyData = instantEmailTemplate.getBodyTemplate().getData();

        final TemplateInstance subjectTemplate = this.templateService.compileTemplate(subjectData, "subject");
        final TemplateInstance bodyTemplate = this.templateService.compileTemplate(bodyData, "body");

        final String subject = this.templateService.renderTemplate(event, subjectTemplate);
        final String body = this.templateService.renderTemplate(event, bodyTemplate);

        // Get the set of user IDs that should receive an email notification for
        // the given event.
        final List<String> subscribers = this.getSubscribers(event);
        if (subscribers.isEmpty()) {
            Log.debugf("[event_uuid: %s] The event was skipped because there were no subscribers for it", event.getId());
            return;
        }

        // Prepare all the data to be sent to the connector.
        final EmailNotification emailNotification = new EmailNotification(
            subscribers,
            recipientSettings,
            body,
            subject
        );

        final JsonObject payload = JsonObject.mapFrom(emailNotification);

        final Endpoint endpoint = this.endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION);

        // The email connector is named "email", and that is what it expects
        // in the Kafka header which specifies the target connector that should
        // process the message. By setting the subtype to "email", we make sure
        // that the connector sender actually sets that exact text.
        endpoint.setSubType("email");
        this.connectorSender.send(event, endpoint, payload);
    }
}
