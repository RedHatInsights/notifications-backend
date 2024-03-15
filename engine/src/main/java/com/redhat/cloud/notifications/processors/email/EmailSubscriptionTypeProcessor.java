package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
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
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.templates.models.DailyDigestSection;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    protected static final String TAG_KEY_ORG_ID = "orgid";

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
    EngineConfig engineConfig;

    @Inject
    ActionParser actionParser;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    BundleRepository bundleRepository;

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
        if (engineConfig.isAsyncAggregationEnabled()) {
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

        List<AggregationCommand> aggregationCommands = new ArrayList<>();
        Timer.Sample consumedTimer = Timer.start(registry);

        Boolean isSingleDailyDigestEnabled = false;
        try {
            Action action = actionParser.fromJsonString(event.getPayload());
            if (null != action.getContext() && null != action.getContext().getAdditionalProperties()) {
                isSingleDailyDigestEnabled = (Boolean) action.getContext().getAdditionalProperties().getOrDefault("single_daily_digest_enabled", false);
            }
            for (com.redhat.cloud.notifications.ingress.Event actionEvent : action.getEvents()) {
                try {
                    aggregationCommands.add(objectMapper.convertValue(actionEvent.getPayload().getAdditionalProperties(), AggregationCommand.class));
                } catch (Exception e) {
                    Log.error("Kafka aggregation payload parsing failed for event: " + event.getId() + ", aggregation: " + actionEvent.toString(), e);
                    rejectedAggregationCommandCount.increment();
                }
            }
        } catch (Exception e) {
            Log.error("Kafka aggregation payload parsing failed for event " + event.getId(), e);
            rejectedAggregationCommandCount.increment();
            return;
        }

        final String bundle  = aggregationCommands.get(0).getAggregationKey().getBundle();

        processedAggregationCommandCount.increment(aggregationCommands.size());
        try {
            if (null != isSingleDailyDigestEnabled && isSingleDailyDigestEnabled) {
                processBundleAggregation(aggregationCommands, event);
            } else {
                processAggregateEmailsByAggregationKey(aggregationCommands.get(0), event);
            }
        } catch (Exception e) {
            Log.warn("Error while processing aggregation", e);
            failedAggregationCommandCount.increment();
        } finally {
            if (aggregationCommands.size() == 1) {
                consumedTimer.stop(registry.timer(
                    AGGREGATION_CONSUMED_TIMER_NAME,
                    TAG_KEY_BUNDLE, bundle,
                    TAG_KEY_APPLICATION, aggregationCommands.get(0).getAggregationKey().getApplication(),
                    TAG_KEY_ORG_ID, event.getOrgId()
                ));
            } else {
                consumedTimer.stop(registry.timer(
                    AGGREGATION_CONSUMED_TIMER_NAME,
                    TAG_KEY_BUNDLE, bundle,
                    TAG_KEY_ORG_ID, event.getOrgId()
                ));
            }
        }
    }

    private void processAggregateEmailsByAggregationKey(AggregationCommand aggregationCommand, Event aggregatorEvent) {

        Log.infof("Processing received aggregation command: %s", aggregationCommand);
        Application app = applicationRepository.getApplication(aggregationCommand.getAggregationKey().getBundle(), aggregationCommand.getAggregationKey().getApplication())
                .orElseThrow(() -> {
                    String exceptionMsg = String.format("Application not found: %s/%s", aggregationCommand.getAggregationKey().getBundle(), aggregationCommand.getAggregationKey().getApplication());
                    return new IllegalArgumentException(exceptionMsg);
                });

        String eventTypeDisplayName = String.format("%s - %s - %s",
            aggregatorEvent.getEventTypeDisplayName(),
            app.getDisplayName(),
            app.getBundle().getDisplayName()
        );
        eventRepository.updateEventDisplayName(aggregatorEvent.getId(), eventTypeDisplayName);

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

        Event event = aggregatorEvent;

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
            Map<User, Map<String, Object>> aggregationsByUsers = emailAggregator.getAggregated(app.getId(), aggregationKey,
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
                String bodyStr = templateService.renderTemplate(event.getEventWrapper().getEvent(), body, emailActorsResolver.getPendoEmailMessage(event));

                Set<RecipientSettings> recipientSettings = extractAndTransformRecipientSettings(event, List.of(endpoint));

                // Prepare all the data to be sent to the connector.
                final EmailNotification emailNotification = new EmailNotification(
                    bodyStr,
                    subjectStr,
                    emailActorsResolver.getEmailSender(event),
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

    private void processBundleAggregation(List<AggregationCommand> aggregationCommands, Event aggregatorEvent) {
        final String bundleName = aggregationCommands.get(0).getAggregationKey().getBundle();
        // Patch event display name for event log rendering
        Bundle bundle = bundleRepository.getBundle(bundleName)
                .orElseThrow(() -> {
                    String exceptionMsg = String.format("Bundle not found: %s", bundleName);
                    return new IllegalArgumentException(exceptionMsg);
                });

        String eventTypeDisplayName = String.format("%s - %s",
            aggregatorEvent.getEventTypeDisplayName(),
            bundle.getDisplayName());
        eventRepository.updateEventDisplayName(aggregatorEvent.getId(), eventTypeDisplayName);

        Endpoint endpoint = endpointRepository.getOrCreateDefaultSystemSubscription(null, aggregatorEvent.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION);

        //Store every aggregated application data for each user
        Map<User, List<ApplicationAggregatedData>> userData = new HashMap<>();

        for (AggregationCommand applicationAggregationCommand : aggregationCommands) {
            try {
                Application app = applicationRepository.getApplication(applicationAggregationCommand.getAggregationKey().getBundle(), applicationAggregationCommand.getAggregationKey().getApplication())
                        .orElseThrow(() -> {
                            String exceptionMsg = String.format("Application not found: %s/%s",
                                    applicationAggregationCommand.getAggregationKey().getBundle(), applicationAggregationCommand.getAggregationKey().getApplication());
                            return new IllegalArgumentException(exceptionMsg);
                        });
                Map<User, Map<String, Object>> applicationAggregatedContextByUser = emailAggregator.getAggregated(app.getId(), applicationAggregationCommand.getAggregationKey(),
                    applicationAggregationCommand.getSubscriptionType(),
                    applicationAggregationCommand.getStart(),
                    applicationAggregationCommand.getEnd());

                applicationAggregatedContextByUser.entrySet().stream().forEach(userAggregation -> {
                    userData.computeIfAbsent(userAggregation.getKey(), unused -> new ArrayList<>())
                        .add(new ApplicationAggregatedData(userAggregation.getValue(), applicationAggregationCommand.getAggregationKey().getApplication()));
                });
            } catch (Exception ex) {
                Log.error("Error processing " + applicationAggregationCommand.getAggregationKey(), ex);
            }
        }

        // Group users with same aggregated data
        Map<List<ApplicationAggregatedData>, Set<User>> usersWithSameAggregatedData = userData.keySet().stream()
            .collect(Collectors.groupingBy(userData::get, Collectors.toSet()));

        String emailTitle = "Daily digest - " + bundle.getDisplayName();

        // for each set of users, generate email subject + body and send it to email connector
        usersWithSameAggregatedData.entrySet().stream().forEach(listApplicationWithUserCollection -> {
            Map<String, DailyDigestSection> dataMap  = new HashMap<>();
            for (ApplicationAggregatedData applicationAggregatedData : listApplicationWithUserCollection.getKey()) {
                try {
                    dataMap.put(applicationAggregatedData.appName, generateAggregatedEmailBody(bundleName, applicationAggregatedData.appName, applicationAggregatedData.aggregatedData, dataMap));
                } catch (Exception ex) {
                    Log.error("Error rendering application template for " + applicationAggregatedData.appName, ex);
                }
            }

            if (!dataMap.isEmpty()) {
                // sort application by name
                List<DailyDigestSection> result = dataMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .toList();

                // get single daily template
                Optional<Template> dailyTemplate = templateRepository.findTemplateByName("Common/insightsDailyEmailBody");
                TemplateInstance SingleBodyTemplate = templateService.compileTemplate(dailyTemplate.get().getData(), "singleDailyDigest/dailyDigest");

                Map<String, Object> action = Map.of("context", Map.of("title", emailTitle, "items", result),
                    "bundle", bundle);

                // build final body
                String bodyStr = templateService.renderTemplate(action, SingleBodyTemplate);

                // Format data to send to the connector.
                Set<String> recipientsUsernames = listApplicationWithUserCollection.getValue().stream().map(User::getUsername).collect(Collectors.toSet());
                Set<RecipientSettings> recipientSettings = extractAndTransformRecipientSettings(aggregatorEvent, List.of(endpoint));

                // Prepare all the data to be sent to the connector.
                final EmailNotification emailNotification = new EmailNotification(
                    bodyStr,
                    emailTitle,
                    this.emailActorsResolver.getEmailSender(aggregatorEvent),
                    aggregatorEvent.getOrgId(),
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

                connectorSender.send(aggregatorEvent, endpoint, JsonObject.mapFrom(emailNotification));
            }
        });

        for (AggregationCommand applicationAggregationCommand : aggregationCommands) {
            emailAggregationRepository.purgeOldAggregation(applicationAggregationCommand.getAggregationKey(), applicationAggregationCommand.getEnd());
        }
    }

    protected DailyDigestSection generateAggregatedEmailBody(String bundle, String app, Map<String, Object> context, Map<String, DailyDigestSection> dataMap) {
        context.put("application", app);
        AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(bundle, app, SubscriptionType.DAILY).get();
        String emailBody = emailTemplate.getBodyTemplate().getData().replace("Common/insightsEmailBody", "Common/insightsEmailBodyLight");
        TemplateInstance templateInstance = templateService.compileTemplate(emailBody, emailTemplate.getBodyTemplate().getName());
        Map<String, Object> action =  Map.of("context", context, "bundle", bundle);

        String result = templateService.renderTemplate(action, templateInstance);

        return addItem(result);
    }

    private DailyDigestSection addItem(String template) {
        String titleData = template.split("<!-- Body section -->")[0];
        String bodyData = template.split("<!-- Body section -->")[1];
        String[] sections = titleData.split("<!-- next section -->");

        return new DailyDigestSection(bodyData, Arrays.stream(sections).filter(e -> !e.isBlank()).collect(Collectors.toList()));
    }

    public class ApplicationAggregatedData {
        Map<String, Object> aggregatedData;
        String appName;

        public ApplicationAggregatedData(Map<String, Object> aggregatedData, String appName) {
            this.aggregatedData = aggregatedData;
            this.appName = appName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ApplicationAggregatedData that = (ApplicationAggregatedData) o;
            return Objects.equals(aggregatedData, that.aggregatedData) && Objects.equals(appName, that.appName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(aggregatedData, appName);
        }
    }

    public static class ApplicationRenderedHtmlBody {
        Set<User> users;
        String htmlBody;

        public ApplicationRenderedHtmlBody(Set<User> users, String htmlBody) {
            this.users = users;
            this.htmlBody = htmlBody;
        }

        public Set<User> getUsers() {
            return users;
        }

        public String getHtmlBody() {
            return htmlBody;
        }
    }
}
