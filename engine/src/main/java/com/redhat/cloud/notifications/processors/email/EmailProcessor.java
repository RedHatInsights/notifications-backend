package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailSenderDefaultRecipientDTO;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.templates.TemplateService;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;

@ApplicationScoped
public class EmailProcessor extends SystemEndpointTypeProcessor {
    @Inject
    ConnectorSender connectorSender;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EmailActorsResolver emailActorsResolver;

    @Inject
    EmailSubscriptionTypeProcessor emailSubscriptionTypeProcessor;

    @Inject
    TemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Inject
    SubscriptionRepository subscriptionRepository;

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

        Set<String> subscribers;
        Set<String> unsubscribers;
        if (event.getEventType().isSubscribedByDefault()) {
            subscribers = Collections.emptySet();
            unsubscribers = Set.copyOf(subscriptionRepository.getUnsubscribers(event.getOrgId(), event.getEventType().getId(), INSTANT));
        } else {
            subscribers = Set.copyOf(subscriptionRepository.getSubscribers(event.getOrgId(), event.getEventType().getId(), INSTANT));
            unsubscribers = Collections.emptySet();
        }

        final Set<RecipientSettings> recipientSettings = this.extractAndTransformRecipientSettings(event, endpoints);

        /*
         * When:
         * - the event type is NOT subscribed by default
         * - the user preferences are NOT ignored
         * - there are no subscribers to the event type
         * Then, there's no need to further process the recipient settings because no email will be sent from them.
         */
        recipientSettings.removeIf(settings -> !event.getEventType().isSubscribedByDefault() && !settings.isIgnoreUserPreferences() && subscribers.isEmpty());

        // If we removed all recipient settings, it means no email will be sent from the event and we can exit this method.
        if (recipientSettings.isEmpty()) {
            Log.debugf("[event_uuid: %s] The event was skipped because there were no subscribers for it and user preferences are not ignored", event.getId());
            return;
        }

        // Render the subject and the body of the email.
        final InstantEmailTemplate instantEmailTemplate = instantEmailTemplateMaybe.get();

        final String subjectData = instantEmailTemplate.getSubjectTemplate().getData();
        final String bodyData = instantEmailTemplate.getBodyTemplate().getData();

        final TemplateInstance subjectTemplate = this.templateService.compileTemplate(subjectData, "subject");
        final TemplateInstance bodyTemplate = this.templateService.compileTemplate(bodyData, "body");

        final String subject = this.templateService.renderTemplate(event.getEventWrapper().getEvent(), subjectTemplate);
        final String body = this.templateService.renderTemplate(event.getEventWrapper().getEvent(), bodyTemplate);

        // Determine which sender and default recipients should be set in the
        // email.
        final EmailSenderDefaultRecipientDTO emailSenderDefaultRecipientDTO = this.emailActorsResolver.getEmailSenderAndDefaultRecipient(event);

        // Prepare all the data to be sent to the connector.
        final EmailNotification emailNotification = new EmailNotification(
            body,
            subject,
            emailSenderDefaultRecipientDTO.emailSender(),
            emailSenderDefaultRecipientDTO.defaultRecipient(),
            event.getOrgId(),
            recipientSettings,
            subscribers,
            unsubscribers,
            event.getEventType().isSubscribedByDefault()
        );

        final JsonObject payload = JsonObject.mapFrom(emailNotification);

        final Endpoint endpoint = this.endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION);

        this.connectorSender.send(event, endpoint, payload);
    }
}
