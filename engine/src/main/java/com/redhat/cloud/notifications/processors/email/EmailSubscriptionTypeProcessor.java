package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.configuration.ProfileManager;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.quarkus.runtime.LaunchMode.NORMAL;

@ApplicationScoped
public class EmailSubscriptionTypeProcessor extends SystemEndpointTypeProcessor {

    public static final String AGGREGATION_CHANNEL = "aggregation";
    public static final String AGGREGATION_COMMAND_REJECTED_COUNTER_NAME = "aggregation.command.rejected";
    public static final String AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME = "aggregation.command.processed";
    public static final String AGGREGATION_COMMAND_ERROR_COUNTER_NAME = "aggregation.command.error";

    public static final String AGGREGATION_CONSUMED_TIMER_NAME = "aggregation.time.consumed";
    protected static final String TAG_KEY_BUNDLE = "bundle";
    protected static final String TAG_KEY_APPLICATION = "application";

    private static final List<EmailSubscriptionType> NON_INSTANT_SUBSCRIPTION_TYPES = Arrays.stream(EmailSubscriptionType.values())
            .filter(emailSubscriptionType -> emailSubscriptionType != EmailSubscriptionType.INSTANT)
            .collect(Collectors.toList());

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    EmailSender emailSender;

    @Inject
    EmailAggregator emailAggregator;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry registry;

    @Inject
    TemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Inject
    FeatureFlipper featureFlipper;

    private Counter rejectedAggregationCommandCount;
    private Counter processedAggregationCommandCount;
    private Counter failedAggregationCommandCount;

    @ConfigProperty(name = "notifications.single.email.test.user")
    String singleEmailTestUser;

    @PostConstruct
    void postConstruct() {
        rejectedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME);
        processedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME);
        failedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_ERROR_COUNTER_NAME);
    }

    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        if (endpoints != null && !endpoints.isEmpty()) {
            this.generateAggregationWhereDue(event);

            sendEmail(event, Set.copyOf(endpoints));
        }
    }

    /**
     * In the case that the event and the event type support aggregations, a
     * new one will be generated in the database. The event is left untouched.
     * @param event the event to be included, or not, in the aggregation.
     */
    public void generateAggregationWhereDue(final Event event) {
        // TODO: Check if we should be using the eventType or application's id instead of the name
        final EventType eventType = event.getEventType();
        final String bundleName = eventType.getApplication().getBundle().getName();
        final String applicationName = eventType.getApplication().getName();

        final boolean shouldSaveAggregation = this.templateRepository.isEmailAggregationSupported(bundleName, applicationName, NON_INSTANT_SUBSCRIPTION_TYPES);

        if (shouldSaveAggregation) {
            final EmailAggregation aggregation = new EmailAggregation();
            aggregation.setOrgId(event.getOrgId());
            aggregation.setApplicationName(applicationName);
            aggregation.setBundleName(bundleName);

            final JsonObject transformedEvent = this.baseTransformer.toJsonObject(event);
            aggregation.setPayload(transformedEvent);
            try {
                this.emailAggregationRepository.addEmailAggregation(aggregation);
            } catch (Exception e) {
                // ConstraintViolationException may be thrown here and it must not interrupt the email that is being sent.
                Log.warn("Email aggregation persisting failed", e);
            }
        }
    }

    private void sendEmail(Event event, Set<Endpoint> endpoints) {
        final TemplateInstance subject;
        final TemplateInstance body;

        Optional<InstantEmailTemplate> instantEmailTemplate = templateRepository
                .findInstantEmailTemplate(event.getEventType().getId());
        if (instantEmailTemplate.isEmpty()) {
            return;
        } else {
            String subjectData = instantEmailTemplate.get().getSubjectTemplate().getData();
            subject = templateService.compileTemplate(subjectData, "subject");
            String bodyData = instantEmailTemplate.get().getBodyTemplate().getData();
            body = templateService.compileTemplate(bodyData, "body");
        }

        Set<User> userList = getRecipientList(event, endpoints.stream().toList(), EmailSubscriptionType.INSTANT);
        if (isSendSingleEmailForMultipleRecipientsEnabled(userList)) {
            emailSender.sendEmail(userList, event, subject, body, true);
        } else {
            for (User user : userList) {
                emailSender.sendEmail(user, event, subject, body, true);
            }
        }
    }

    @Incoming(AGGREGATION_CHANNEL)
    @Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
    @Blocking
    @ActivateRequestContext
    public void consumeEmailAggregations(String aggregationCommandJson) {
        Timer.Sample consumedTimer = Timer.start(registry);
        AggregationCommand aggregationCommand;
        try {
            aggregationCommand = objectMapper.readValue(aggregationCommandJson, AggregationCommand.class);
        } catch (JsonProcessingException e) {
            Log.error("Kafka aggregation payload parsing failed", e);
            rejectedAggregationCommandCount.increment();
            return;
        }

        Log.infof("Processing received aggregation command: %s", aggregationCommand);
        processedAggregationCommandCount.increment();

        try {
            processAggregateEmailsByAggregationKey(
                    aggregationCommand.getAggregationKey(),
                    aggregationCommand.getStart(),
                    aggregationCommand.getEnd(),
                    aggregationCommand.getSubscriptionType(),
                    // Delete on daily
                    aggregationCommand.getSubscriptionType().equals(EmailSubscriptionType.DAILY));
        } catch (Exception e) {
            Log.warn("Error while processing aggregation", e);
            failedAggregationCommandCount.increment();
        } finally {
            consumedTimer.stop(registry.timer(
                AGGREGATION_CONSUMED_TIMER_NAME,
                TAG_KEY_BUNDLE, aggregationCommand.getAggregationKey().getBundle(),
                TAG_KEY_APPLICATION, aggregationCommand.getAggregationKey().getApplication()
            ));
        }
    }

    private void processAggregateEmailsByAggregationKey(EmailAggregationKey aggregationKey, LocalDateTime startTime, LocalDateTime endTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {
        TemplateInstance subject = null;
        TemplateInstance body = null;

        Optional<AggregationEmailTemplate> aggregationEmailTemplate = templateRepository
                .findAggregationEmailTemplate(aggregationKey.getBundle(), aggregationKey.getApplication(), emailSubscriptionType);
        if (aggregationEmailTemplate.isPresent()) {
            String subjectData = aggregationEmailTemplate.get().getSubjectTemplate().getData();
            subject = templateService.compileTemplate(subjectData, "subject");
            String bodyData = aggregationEmailTemplate.get().getBodyTemplate().getData();
            body = templateService.compileTemplate(bodyData, "body");
        }

        if (subject != null && body != null) {
            Map<User, Map<String, Object>> aggregationsByUsers = emailAggregator.getAggregated(aggregationKey, emailSubscriptionType, startTime, endTime);

            if (isSendSingleEmailForMultipleRecipientsEnabled(aggregationsByUsers.keySet())) {
                Map<Map<String, Object>, Set<User>> aggregationsEmailContext = aggregationsByUsers.keySet().stream()
                    .collect(Collectors.groupingBy(aggregationsByUsers::get, Collectors.toSet()));

                for (Map.Entry<Map<String, Object>, Set<User>> aggregation : aggregationsEmailContext.entrySet()) {

                    Context.ContextBuilder contextBuilder = new Context.ContextBuilder();
                    aggregation.getKey().forEach(contextBuilder::withAdditionalProperty);

                    Action action = new Action();
                    action.setContext(contextBuilder.build());
                    action.setEvents(List.of());
                    action.setOrgId(aggregationKey.getOrgId());
                    action.setApplication(aggregationKey.getApplication());
                    action.setBundle(aggregationKey.getBundle());

                    // We don't have an event type as this aggregates over multiple event types
                    action.setEventType(null);
                    action.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

                    Event event = new Event();
                    event.setId(UUID.randomUUID());
                    event.setEventWrapper(new EventWrapperAction(action));

                    emailSender.sendEmail(aggregation.getValue(), event, subject, body, false);
                }
            } else {
                for (Map.Entry<User, Map<String, Object>> aggregation : aggregationsByUsers.entrySet()) {

                    Context.ContextBuilder contextBuilder = new Context.ContextBuilder();
                    aggregation.getValue().forEach(contextBuilder::withAdditionalProperty);

                    Action action = new Action();
                    action.setContext(contextBuilder.build());
                    action.setEvents(List.of());
                    action.setOrgId(aggregationKey.getOrgId());
                    action.setApplication(aggregationKey.getApplication());
                    action.setBundle(aggregationKey.getBundle());

                    // We don't have an event type as this aggregates over multiple event types
                    action.setEventType(null);
                    action.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

                    Event event = new Event();
                    event.setId(UUID.randomUUID());
                    event.setEventWrapper(new EventWrapperAction(action));

                    emailSender.sendEmail(aggregation.getKey(), event, subject, body, false);
                }
            }

        }

        if (delete) {
            emailAggregationRepository.purgeOldAggregation(aggregationKey, endTime);
        }
    }

    private boolean isSendSingleEmailForMultipleRecipientsEnabled(Set<User> users) {
        if (ProfileManager.getLaunchMode() == NORMAL && featureFlipper.isSendSingleEmailForMultipleRecipientsEnabled()) {
            Set<String> strUsers = users.stream().map(User::getUsername).collect(Collectors.toSet());
            Log.infof("Email test username is %s", singleEmailTestUser);
            return (strUsers.contains(singleEmailTestUser));
        }
        return featureFlipper.isSendSingleEmailForMultipleRecipientsEnabled();
    }
}
