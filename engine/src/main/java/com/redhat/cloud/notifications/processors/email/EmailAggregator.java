package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.email.aggregators.AbstractEmailPayloadAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.EmailPayloadAggregatorFactory;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import com.redhat.cloud.notifications.templates.EmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import com.redhat.cloud.notifications.templates.TemplateService;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class EmailAggregator {

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    RecipientResolver recipientResolver;

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @Inject
    TemplateService templateService;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    TemplateRepository templateRepository;

    @Inject
    EmailTemplateFactory emailTemplateFactory;

    // This is manually used from the JSON payload instead of converting it to an Action and using getEventType()
    private static final String EVENT_TYPE_KEY = "event_type";
    private static final String APPLICATION_KEY = "application";
    private static final String RECIPIENTS_KEY = "recipients";

    public Set<String> getEmailSubscribers(EmailAggregationKey aggregationKey, EmailSubscriptionType emailSubscriptionType) {
        return Set.copyOf(emailSubscriptionRepository
                .getBundleEmailSubscribersUserId(aggregationKey.getOrgId(), aggregationKey.getBundle(), emailSubscriptionType));
    }

    public Optional<AggregatedData> getAggregated(EmailAggregationKey aggregationKey, String username, LocalDateTime start, LocalDateTime end) {
        Map</* application */ String, AbstractEmailPayloadAggregator> aggregated = new HashMap<>();
        List<EmailAggregation> aggregations = emailAggregationRepository.getEmailAggregation(aggregationKey, start, end);

        AggregatedData aggregatedData = new AggregatedData();

        for (EmailAggregation aggregation : aggregations) {
            String eventType = getEventType(aggregation);
            String application = getApplication(aggregation);
            // Let's retrieve these targets.
            Set<Endpoint> endpoints = Set.copyOf(endpointRepository
                    .getTargetEmailSubscriptionEndpoints(aggregationKey.getOrgId(), aggregationKey.getBundle(), application, eventType));

            Set<User> targetUsers = recipientResolver.recipientUsers(
                    aggregationKey.getOrgId(),
                    Stream.concat(
                            endpoints
                                    .stream()
                                    .map(EndpointRecipientSettings::new),
                            getActionRecipient(aggregation).stream()
                    ).collect(Collectors.toSet()),
                    Set.of(username)
            );

            targetUsers
                    .stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst()
                    .ifPresent(user -> {
                        aggregatedData.user = user;
                        AbstractEmailPayloadAggregator aggregator = aggregated.computeIfAbsent(application, ignoredApplication -> EmailPayloadAggregatorFactory.by(aggregationKey.getBundle(), application, start, end));
                        if (aggregator != null) {
                            aggregator.aggregate(aggregation);
                        }
                    });
        }

        if (aggregatedData.user == null) {
            return Optional.empty();
        }

        aggregatedData.aggregatedDataByApplication = aggregated.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getContext()));

        return Optional.of(aggregatedData);
    }

    public Optional<TemplateInstance> getTemplate(String bundle, String application, EmailSubscriptionType emailSubscriptionType) {
        if (featureFlipper.isUseTemplatesFromDb()) {
            Optional<AggregationEmailTemplate> aggregationEmailTemplate = templateRepository
                    .findAggregationEmailTemplate(bundle, application, emailSubscriptionType);
            if (aggregationEmailTemplate.isPresent()) {
                String bodyData = aggregationEmailTemplate.get().getBodyTemplate().getData();
                return Optional.of(templateService.compileTemplate(bodyData, "body"));
            }
        } else {
            EmailTemplate emailTemplate = emailTemplateFactory.get(bundle, application);
            if (emailTemplate.isEmailSubscriptionSupported(emailSubscriptionType)) {
                return Optional.of(emailTemplate.getBody(null, emailSubscriptionType));
            }
        }

        return Optional.empty();
    }

    public String resolveTemplates(User user, EmailAggregationKey aggregationKey, String application, TemplateInstance template, Map<String, Object> data) {
        Context.ContextBuilder contextBuilder = new Context.ContextBuilder();
        data.forEach(contextBuilder::withAdditionalProperty);

        Action action = new Action();
        action.setContext(contextBuilder.build());
        action.setEvents(List.of());
        action.setOrgId(aggregationKey.getOrgId());
        action.setBundle(aggregationKey.getBundle());
        action.setApplication(application);

        // We don't have an event type as this aggregates over multiple event types
        action.setEventType(null);
        action.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setAction(action);

        return templateService.renderTemplate(user, action, template);
    }

    private String getEventType(EmailAggregation aggregation) {
        return aggregation.getPayload().getString(EVENT_TYPE_KEY);
    }

    private String getApplication(EmailAggregation aggregation) {
        return aggregation.getPayload().getString(APPLICATION_KEY);
    }

    private List<ActionRecipientSettings> getActionRecipient(EmailAggregation emailAggregation) {
        if (emailAggregation.getPayload().containsKey(RECIPIENTS_KEY)) {
            JsonArray recipients = emailAggregation.getPayload().getJsonArray(RECIPIENTS_KEY);
            if (recipients.size() > 0) {
                return recipients.stream().map(r -> {
                    JsonObject recipient = (JsonObject) r;
                    return recipient.mapTo(Recipient.class);
                }).map(ActionRecipientSettings::new).collect(Collectors.toList());
            }

        }
        return List.of();
    }

}
