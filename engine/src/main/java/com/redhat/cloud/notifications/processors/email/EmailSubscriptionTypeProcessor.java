package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import com.redhat.cloud.notifications.templates.Default;
import com.redhat.cloud.notifications.templates.EmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.vertx.core.json.JsonObject;
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
import java.util.stream.Stream;

@ApplicationScoped
public class EmailSubscriptionTypeProcessor extends EndpointTypeProcessor {

    public static final String AGGREGATION_CHANNEL = "aggregation";
    public static final String AGGREGATION_COMMAND_REJECTED_COUNTER_NAME = "aggregation.command.rejected";
    public static final String AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME = "aggregation.command.processed";
    public static final String AGGREGATION_COMMAND_ERROR_COUNTER_NAME = "aggregation.command.error";

    private static final List<EmailSubscriptionType> NON_INSTANT_SUBSCRIPTION_TYPES = Arrays.stream(EmailSubscriptionType.values())
            .filter(emailSubscriptionType -> emailSubscriptionType != EmailSubscriptionType.INSTANT)
            .collect(Collectors.toList());

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

    @Inject
    TemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Inject
    FeatureFlipper featureFlipper;

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
    public void process(Event event, List<Endpoint> endpoints) {
        if (endpoints != null && !endpoints.isEmpty()) {
            Action action = event.getAction();
            boolean shouldSaveAggregation;
            if (featureFlipper.isUseTemplatesFromDb()) {
                shouldSaveAggregation = templateRepository.isEmailAggregationSupported(action.getBundle(), action.getApplication(), NON_INSTANT_SUBSCRIPTION_TYPES);
            } else {
                EmailTemplate template = emailTemplateFactory.get(action.getBundle(), action.getApplication());
                shouldSaveAggregation = NON_INSTANT_SUBSCRIPTION_TYPES.stream()
                        .anyMatch(emailSubscriptionType -> template.isSupported(action.getEventType(), emailSubscriptionType));
            }

            if (shouldSaveAggregation) {
                EmailAggregation aggregation = new EmailAggregation();
                aggregation.setOrgId(action.getOrgId());
                aggregation.setApplicationName(action.getApplication());
                aggregation.setBundleName(action.getBundle());

                JsonObject transformedAction = baseTransformer.toJsonObject(action);
                aggregation.setPayload(transformedAction);
                emailAggregationRepository.addEmailAggregation(aggregation);
            }

            sendEmail(event, Set.copyOf(endpoints));
        }
    }

    private void sendEmail(Event event, Set<Endpoint> endpoints) {
        EmailSubscriptionType emailSubscriptionType = EmailSubscriptionType.INSTANT;
        processedEmailCount.increment();
        Action action = event.getAction();

        final TemplateInstance subject;
        final TemplateInstance body;

        if (featureFlipper.isUseTemplatesFromDb()) {
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
        } else {
            EmailTemplate emailTemplate = emailTemplateFactory.get(action.getBundle(), action.getApplication());
            if (!emailTemplate.isSupported(action.getEventType(), emailSubscriptionType)) {
                return;
            }

            TemplateInstance fileTemplateSubject = emailTemplate.getTitle(action.getEventType(), emailSubscriptionType);
            TemplateInstance fileTemplateBody = emailTemplate.getBody(action.getEventType(), emailSubscriptionType);

            if (fileTemplateSubject == null || fileTemplateBody == null) {
                if (fileTemplateSubject != null || fileTemplateBody != null) {
                    Log.warnf("Only one of the subject and body was found for event: %s/%s/%s", event.getBundleDisplayName(), event.getApplicationDisplayName(), event.getEventTypeDisplayName());
                }

                if (featureFlipper.isUseDefaultTemplate()) {
                    fileTemplateBody = Default.getBody(fileTemplateSubject != null, fileTemplateBody != null);
                    fileTemplateSubject = Default.getTitle();
                } else {
                    return;
                }
            }

            subject = fileTemplateSubject;
            body = fileTemplateBody;
        }

        Set<RecipientSettings> requests = Stream.concat(
                endpoints.stream().map(EndpointRecipientSettings::new),
                ActionRecipientSettings.fromAction(action).stream()
        ).collect(Collectors.toSet());

        Set<String> subscribers = Set.copyOf(emailSubscriptionRepository
                .getEmailSubscribersUserId(action.getOrgId(), action.getBundle(), action.getApplication(), emailSubscriptionType));

        for (User user : recipientResolver.recipientUsers(action.getOrgId(), requests, subscribers)) {
            emailSender.sendEmail(user, event, subject, body, true);
        }
    }

    @Incoming(AGGREGATION_CHANNEL)
    @Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
    @Blocking
    @ActivateRequestContext
    public void consumeEmailAggregations(String aggregationCommandJson) {
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
            Log.info("Error while processing aggregation", e);
            failedAggregationCommandCount.increment();
        }
    }

    private void processAggregateEmailsByAggregationKey(EmailAggregationKey aggregationKey, LocalDateTime startTime, LocalDateTime endTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {
        TemplateInstance subject = null;
        TemplateInstance body = null;

        if (featureFlipper.isUseTemplatesFromDb()) {
            Optional<AggregationEmailTemplate> aggregationEmailTemplate = templateRepository
                    .findAggregationEmailTemplate(aggregationKey.getBundle(), aggregationKey.getApplication(), emailSubscriptionType);
            if (aggregationEmailTemplate.isPresent()) {
                String subjectData = aggregationEmailTemplate.get().getSubjectTemplate().getData();
                subject = templateService.compileTemplate(subjectData, "subject");
                String bodyData = aggregationEmailTemplate.get().getBodyTemplate().getData();
                body = templateService.compileTemplate(bodyData, "body");
            }
        } else {
            EmailTemplate emailTemplate = emailTemplateFactory.get(aggregationKey.getBundle(), aggregationKey.getApplication());
            if (emailTemplate.isEmailSubscriptionSupported(emailSubscriptionType)) {
                subject = emailTemplate.getTitle(null, emailSubscriptionType);
                body = emailTemplate.getBody(null, emailSubscriptionType);
            }
        }

        if (subject != null && body != null) {
            for (Map.Entry<User, Map<String, Object>> aggregation :
                    emailAggregator.getAggregated(aggregationKey, emailSubscriptionType, startTime, endTime).entrySet()) {

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
                event.setAction(action);

                emailSender.sendEmail(aggregation.getKey(), event, subject, body, false);
            }
        }

        if (delete) {
            emailAggregationRepository.purgeOldAggregation(aggregationKey, endTime);
        }
    }
}
