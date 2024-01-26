package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import com.redhat.cloud.notifications.utils.ActionParser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

/*
 * This class needs more cleanup but this will be done later to make the reviews easier.
 * TODO Stop extending SystemEndpointTypeProcessor.
 */
@ApplicationScoped
public class EmailSubscriptionTypeProcessor extends SystemEndpointTypeProcessor {

    public static final String AGGREGATION_COMMAND_REJECTED_COUNTER_NAME = "aggregation.command.rejected";
    public static final String AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME = "aggregation.command.processed";
    public static final String AGGREGATION_COMMAND_ERROR_COUNTER_NAME = "aggregation.command.error";

    public static final String AGGREGATION_CONSUMED_TIMER_NAME = "aggregation.time.consumed";
    protected static final String TAG_KEY_BUNDLE = "bundle";
    protected static final String TAG_KEY_APPLICATION = "application";

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    EmailActorsResolver emailActorsResolver;

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

    @Inject
    ActionParser actionParser;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    EventRepository eventRepository;

    // This executor is used to run a task asynchronously using a worker thread from a threads pool managed by Quarkus.
    @Inject
    @AggregationManagedExecutor
    ManagedExecutor managedExecutor;

    @Inject
    Instance<AsyncAggregation> asyncAggregations;

    private Counter rejectedAggregationCommandCount;
    private Counter processedAggregationCommandCount;
    private Counter failedAggregationCommandCount;

    @Inject
    ConnectorSender connectorSender;

    @PostConstruct
    void postConstruct() {
        rejectedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME);
        processedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME);
        failedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_ERROR_COUNTER_NAME);
    }

    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        throw new UnsupportedOperationException("No longer used");
    }

    /**
     * In the case that the event and the event type support aggregations, a
     * new one will be generated in the database. The event is left untouched.
     * @param event the event to be included, or not, in the aggregation.
     */
    public void generateAggregationWhereDue(final Event event) {
        final EventType eventType = event.getEventType();
        final String bundleName = eventType.getApplication().getBundle().getName();
        final String applicationName = eventType.getApplication().getName();

        final boolean shouldSaveAggregation = this.templateRepository.isEmailAggregationSupported(eventType.getApplicationId());

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

    public void processAggregation(Event event) {
        if (featureFlipper.isAsyncAggregation()) {
            /*
             * The aggregation process is long-running task. To avoid blocking the thread used to consume
             * Kafka messages from the ingress topic, we're performing the aggregation from a worker thread.
             */
            AsyncAggregation asyncAggregation = asyncAggregations.get();
            asyncAggregation.setEvent(event);
            managedExecutor.runAsync(asyncAggregation)
                    .thenRun(() -> {
                        /*
                         * When a @Dependent bean is injected into an @ApplicationScoped bean using Instance<T>,
                         * the dependent bean has to be destroyed manually when it's no longer needed. Otherwise,
                         * instances of the dependent bean accumulate in memory, causing a memory leak.
                         */
                        asyncAggregations.destroy(asyncAggregation);
                    });
        } else {
            processAggregationSync(event);
        }
    }

    @Transactional(REQUIRES_NEW)
    public void processAggregationAsync(Event event) {
        processAggregationSync(event);
    }

    public void processAggregationSync(Event event) {
        AggregationCommand aggregationCommand;
        Timer.Sample consumedTimer = Timer.start(registry);

        try {
            Action action = actionParser.fromJsonString(event.getPayload());
            Map<String, Object> map = action.getEvents().get(0).getPayload().getAdditionalProperties();
            aggregationCommand = objectMapper.convertValue(map, AggregationCommand.class);
        } catch (Exception e) {
            Log.error("Kafka aggregation payload parsing failed for event " + event.getId(), e);
            rejectedAggregationCommandCount.increment();
            return;
        }

        Log.infof("Processing received aggregation command: %s", aggregationCommand);
        processedAggregationCommandCount.increment();

        try {
            Optional<Application> app = applicationRepository.getApplication(aggregationCommand.getAggregationKey().getBundle(), aggregationCommand.getAggregationKey().getApplication());
            if (app.isPresent()) {
                String eventTypeDisplayName = String.format("%s - %s - %s",
                        event.getEventTypeDisplayName(),
                        app.get().getDisplayName(),
                        app.get().getBundle().getDisplayName()
                );
                eventRepository.updateEventDisplayName(event.getId(), eventTypeDisplayName);
            }
            processAggregateEmailsByAggregationKey(aggregationCommand, Optional.of(event));
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

    private void processAggregateEmailsByAggregationKey(AggregationCommand aggregationCommand, Optional<Event> aggregatorEvent) {
        TemplateInstance subject = null;
        TemplateInstance body = null;

        EmailAggregationKey aggregationKey = aggregationCommand.getAggregationKey();
        Optional<AggregationEmailTemplate> aggregationEmailTemplate = templateRepository
                .findAggregationEmailTemplate(aggregationKey.getBundle(), aggregationKey.getApplication(), aggregationCommand.getSubscriptionType());
        if (aggregationEmailTemplate.isPresent()) {
            String subjectData = aggregationEmailTemplate.get().getSubjectTemplate().getData();
            subject = templateService.compileTemplate(subjectData, "subject");
            String bodyData = aggregationEmailTemplate.get().getBodyTemplate().getData();
            body = templateService.compileTemplate(bodyData, "body");
        }

        Endpoint endpoint = endpointRepository.getOrCreateDefaultSystemSubscription(null, aggregationKey.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION);

        Event event;
        if (aggregatorEvent.isEmpty()) {
            event = new Event();
            event.setId(UUID.randomUUID());
        } else {
            event = aggregatorEvent.get();
        }

        Action action = new Action();
        action.setEvents(List.of());
        action.setOrgId(aggregationKey.getOrgId());
        action.setApplication(aggregationKey.getApplication());
        action.setBundle(aggregationKey.getBundle());
        action.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        if (null != event.getEventType()) {
            action.setEventType(event.getEventType().getName());
        }

        if (subject != null && body != null) {
            Map<User, Map<String, Object>> aggregationsByUsers = emailAggregator.getAggregated(aggregationKey,
                                                                        aggregationCommand.getSubscriptionType(),
                                                                        aggregationCommand.getStart(),
                                                                        aggregationCommand.getEnd());

            Map<Map<String, Object>, Set<User>> aggregationsEmailContext = aggregationsByUsers.keySet().stream()
                .collect(Collectors.groupingBy(aggregationsByUsers::get, Collectors.toSet()));

            for (Map.Entry<Map<String, Object>, Set<User>> aggregation : aggregationsEmailContext.entrySet()) {

                Context.ContextBuilder contextBuilder = new Context.ContextBuilder();
                aggregation.getKey().forEach(contextBuilder::withAdditionalProperty);
                action.setContext(contextBuilder.build());
                event.setEventWrapper(new EventWrapperAction(action));

                Set<String> recipientsUsernames = aggregation.getValue().stream().map(User::getUsername).collect(Collectors.toSet());
                String subjectStr = templateService.renderTemplate(event.getEventWrapper().getEvent(), subject);
                String bodyStr = templateService.renderTemplate(event.getEventWrapper().getEvent(), body);

                Set<RecipientSettings> recipientSettings = extractAndTransformRecipientSettings(event, List.of(endpoint));

                // Prepare all the data to be sent to the connector.
                final EmailNotification emailNotification = new EmailNotification(
                    bodyStr,
                    subjectStr,
                    this.emailActorsResolver.getEmailSender(event),
                    event.getOrgId(),
                    recipientSettings,
                    /*
                     * The recipients are determined at an earlier stage (see EmailAggregator) using the
                     * recipients-resolver app and the subscription records from the database.
                     * The subscribedByDefault value below simply means that recipients-resolver will consider
                     * the subscribers passed in the request as the recipients candidates of the aggregation email.
                     */
                    recipientsUsernames,
                    Collections.emptySet(),
                    false
                );

                connectorSender.send(event, endpoint, JsonObject.mapFrom(emailNotification));
            }
        }

        // Delete on daily
        if (aggregationCommand.getSubscriptionType().equals(SubscriptionType.DAILY)) {
            emailAggregationRepository.purgeOldAggregation(aggregationKey, aggregationCommand.getEnd());
        }
    }
}
