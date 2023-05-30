package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.event.TestEventHelper;
import com.redhat.cloud.notifications.processors.camel.google.chat.GoogleChatProcessor;
import com.redhat.cloud.notifications.processors.camel.slack.SlackProcessor;
import com.redhat.cloud.notifications.processors.camel.teams.TeamsProcessor;
import com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor;
import com.redhat.cloud.notifications.processors.eventing.EventingProcessor;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class EndpointProcessor {

    public static final String PROCESSED_MESSAGES_COUNTER_NAME = "processor.input.processed";
    public static final String PROCESSED_ENDPOINTS_COUNTER_NAME = "processor.input.endpoint.processed";
    public static final String DELAYED_EXCEPTION_MSG = "Exceptions were thrown during an event processing";
    public static final String SLACK_ENDPOINT_SUBTYPE = "slack";
    public static final String TEAMS_ENDPOINT_SUBTYPE = "teams";
    public static final String GOOGLE_CHAT_ENDPOINT_SUBTYPE = "google_chat";

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    WebhookTypeProcessor webhookProcessor;

    @Inject
    EventingProcessor camelProcessor;

    @Inject
    EmailSubscriptionTypeProcessor emailProcessor;

    @Inject
    SlackProcessor slackProcessor;

    @Inject
    TeamsProcessor teamsProcessor;

    @Inject
    GoogleChatProcessor googleChatProcessor;

    @Inject
    MeterRegistry registry;

    private Counter processedItems;
    private Counter endpointTargeted;

    @PostConstruct
    void init() {
        processedItems = registry.counter(PROCESSED_MESSAGES_COUNTER_NAME);
        endpointTargeted = registry.counter(PROCESSED_ENDPOINTS_COUNTER_NAME);
    }

    public void process(Event event) {
        processedItems.increment();

        final List<Endpoint> endpoints;
        if (TestEventHelper.isIntegrationTestEvent(event)) {
            final UUID endpointUuid = TestEventHelper.extractEndpointUuidFromTestEvent(event);

            final Endpoint endpoint = this.endpointRepository.findByUuidAndOrgId(endpointUuid, event.getOrgId());

            endpoints = List.of(endpoint);
        } else {
            endpoints = endpointRepository.getTargetEndpoints(event.getOrgId(), event.getEventType());
        }

        // Target endpoints are grouped by endpoint type.
        endpointTargeted.increment(endpoints.size());
        Map<EndpointType, List<Endpoint>> endpointsByType = endpoints.stream().collect(Collectors.groupingBy(Endpoint::getType));

        DelayedThrower.throwEventually(DELAYED_EXCEPTION_MSG, accumulator -> {
            for (Map.Entry<EndpointType, List<Endpoint>> endpointsByTypeEntry : endpointsByType.entrySet()) {
                try {
                    // For each endpoint type, the list of target endpoints is sent alongside with the event to the relevant processor.
                    switch (endpointsByTypeEntry.getKey()) {
                        // TODO Introduce EndpointType.SLACK?
                        case CAMEL:
                            Map<String, List<Endpoint>> endpointsBySubType = endpointsByTypeEntry.getValue().stream().collect(Collectors.groupingBy(Endpoint::getSubType));
                            for (Map.Entry<String, List<Endpoint>> endpointsBySubTypeEntry : endpointsBySubType.entrySet()) {
                                try {
                                    if (SLACK_ENDPOINT_SUBTYPE.equals(endpointsBySubTypeEntry.getKey())) {
                                        slackProcessor.process(event, endpointsBySubTypeEntry.getValue());
                                    } else if (TEAMS_ENDPOINT_SUBTYPE.equals(endpointsBySubTypeEntry.getKey())) {
                                        teamsProcessor.process(event, endpointsBySubTypeEntry.getValue());
                                    } else if (GOOGLE_CHAT_ENDPOINT_SUBTYPE.equals(endpointsBySubTypeEntry.getKey())) {
                                        googleChatProcessor.process(event, endpointsBySubTypeEntry.getValue());
                                    } else {
                                        camelProcessor.process(event, endpointsBySubTypeEntry.getValue());
                                    }
                                } catch (Exception e) {
                                    accumulator.add(e);
                                }
                            }
                            break;
                        case EMAIL_SUBSCRIPTION:
                            emailProcessor.process(event, endpointsByTypeEntry.getValue());
                            break;
                        case WEBHOOK:
                        case ANSIBLE:
                            webhookProcessor.process(event, endpointsByTypeEntry.getValue());
                            break;
                        default:
                            throw new IllegalArgumentException("Unexpected endpoint type: " + endpointsByTypeEntry.getKey());
                    }
                } catch (Exception e) {
                    accumulator.add(e);
                }
            }
        });
    }
}
