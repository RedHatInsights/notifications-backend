package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import com.redhat.cloud.notifications.templates.EmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class EmailSubscriptionTypeProcessor implements EndpointTypeProcessor {

    public static final String AGGREGATION_CHANNEL = "aggregation";
    public static final String AGGREGATION_COMMAND_REJECTED_COUNTER_NAME = "aggregation.command.rejected";
    public static final String AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME = "aggregation.command.processed";
    public static final String AGGREGATION_COMMAND_ERROR_COUNTER_NAME = "aggregation.command.error";

    private static final Logger LOGGER = Logger.getLogger(EmailSubscriptionTypeProcessor.class);

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @Inject
    RecipientResolver recipientResolver;

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    EmailTemplateFactory emailTemplateFactory;

    @Inject
    EmailSender emailSender;

    @Inject
    EmailAggregator emailAggregator;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    MeterRegistry registry;

    private Counter processedEmailCount;
    private Counter rejectedAggregationCommandCount;
    private Counter processedAggregationCommandCount;
    private Counter failedAggregationCommandCount;

    @PostConstruct
    void postConstruct() {
        processedEmailCount = registry.counter("processor.email.processed");
        rejectedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME);
        processedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME);
        failedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_ERROR_COUNTER_NAME);
    }

    @Override
    public List<NotificationHistory> process(Event event, List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return Collections.emptyList();
        } else {
            Action action = event.getAction();
            final EmailTemplate template = emailTemplateFactory.get(action.getBundle(), action.getApplication());
            final boolean shouldSaveAggregation = Arrays.stream(EmailSubscriptionType.values())
                    .filter(emailSubscriptionType -> emailSubscriptionType != EmailSubscriptionType.INSTANT)
                    .anyMatch(emailSubscriptionType -> template.isSupported(action.getEventType(), emailSubscriptionType));

            if (shouldSaveAggregation) {
                EmailAggregation aggregation = new EmailAggregation();
                aggregation.setAccountId(action.getAccountId());
                aggregation.setApplicationName(action.getApplication());
                aggregation.setBundleName(action.getBundle());

                JsonObject transformedAction = baseTransformer.transform(action);
                aggregation.setPayload(transformedAction);
                emailAggregationRepository.addEmailAggregation(aggregation);
            }

            return sendEmail(
                    event,
                    Set.copyOf(endpoints),
                    EmailSubscriptionType.INSTANT,
                    template
            );
        }
    }

    private List<NotificationHistory> sendEmail(Event event, Set<Endpoint> endpoints, EmailSubscriptionType emailSubscriptionType, EmailTemplate emailTemplate) {
        processedEmailCount.increment();
        Action action = event.getAction();
        if (!emailTemplate.isSupported(action.getEventType(), emailSubscriptionType)) {
            return Collections.emptyList();
        }

        TemplateInstance subject = emailTemplate.getTitle(action.getEventType(), emailSubscriptionType);
        TemplateInstance body = emailTemplate.getBody(action.getEventType(), emailSubscriptionType);

        if (subject == null || body == null) {
            return Collections.emptyList();
        }

        Set<RecipientSettings> requests = Stream.concat(
                endpoints.stream().map(EndpointRecipientSettings::new),
                ActionRecipientSettings.fromAction(action).stream()
        ).collect(Collectors.toSet());

        Set<String> subscribers = Set.copyOf(emailSubscriptionRepository
                .getEmailSubscribersUserId(action.getAccountId(), action.getBundle(), action.getApplication(), emailSubscriptionType));
        return recipientResolver.recipientUsers(action.getAccountId(), requests, subscribers)
                .stream()
                .map(user -> emailSender.sendEmail(user, event, subject, body))
                // The value may be an empty Optional in case of Qute template exception.
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Incoming(AGGREGATION_CHANNEL)
    @Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
    @Blocking
    public void consumeEmailAggregations(String aggregationCommandJson) {
        AggregationCommand aggregationCommand;
        try {
            aggregationCommand = objectMapper.readValue(aggregationCommandJson, AggregationCommand.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Kafka aggregation payload parsing failed", e);
            rejectedAggregationCommandCount.increment();
            return;
        }

        LOGGER.infof("Processing received aggregation command: %s", aggregationCommand);
        processedAggregationCommandCount.increment();

        try {
            statelessSessionFactory.withSession(statelessSession -> {
                processAggregateEmailsByAggregationKey(
                        aggregationCommand.getAggregationKey(),
                        aggregationCommand.getStart(),
                        aggregationCommand.getEnd(),
                        aggregationCommand.getSubscriptionType(),
                        // Delete on daily
                        aggregationCommand.getSubscriptionType().equals(EmailSubscriptionType.DAILY));
            });
        } catch (Exception e) {
            LOGGER.info("Error while processing aggregation", e);
            failedAggregationCommandCount.increment();
        }
    }

    private void processAggregateEmailsByAggregationKey(EmailAggregationKey aggregationKey, LocalDateTime startTime, LocalDateTime endTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {
        final EmailTemplate emailTemplate = emailTemplateFactory.get(aggregationKey.getBundle(), aggregationKey.getApplication());

        if (!emailTemplate.isEmailSubscriptionSupported(emailSubscriptionType)) {
            if (delete) {
                emailAggregationRepository.purgeOldAggregation(aggregationKey, endTime);
            }
            return;
        }

        TemplateInstance subject = emailTemplate.getTitle(null, emailSubscriptionType);
        TemplateInstance body = emailTemplate.getBody(null, emailSubscriptionType);

        if (subject == null || body == null) {
            if (delete) {
                emailAggregationRepository.purgeOldAggregation(aggregationKey, endTime);
            }
            return;
        }
        try {
            for (Map.Entry<User, Map<String, Object>> aggregation :
                    emailAggregator.getAggregated(aggregationKey, emailSubscriptionType, startTime, endTime).entrySet()) {

                Action action = new Action();
                action.setContext(aggregation.getValue());
                action.setEvents(List.of());
                action.setAccountId(aggregationKey.getAccountId());
                action.setApplication(aggregationKey.getApplication());
                action.setBundle(aggregationKey.getBundle());

                // We don't have a eventtype as this aggregates over multiple event types
                action.setEventType(null);
                action.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

                Event event = new Event();
                event.setAction(action);

                emailSender.sendEmail(aggregation.getKey(), event, subject, body);
            }
        } finally {
            if (delete) {
                emailAggregationRepository.purgeOldAggregation(aggregationKey, endTime);
            }
        }
    }
}
