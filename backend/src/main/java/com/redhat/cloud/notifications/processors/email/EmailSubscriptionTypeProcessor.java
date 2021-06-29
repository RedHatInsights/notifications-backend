package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.templates.EmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class EmailSubscriptionTypeProcessor implements EndpointTypeProcessor {

    public static final String AGGREGATION_CHANNEL = "aggregation";

    private final Logger log = Logger.getLogger(this.getClass().getName());

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @Inject
    RecipientResolver recipientResolver;

    @Inject
    EmailAggregationResources emailAggregationResources;

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
    Mutiny.SessionFactory sessionFactory;

    @Inject
    MeterRegistry registry;

    private Counter processedCount;

    @PostConstruct
    void postConstruct() {
        processedCount = registry.counter("processor.email.processed");
    }

    @Override
    public Multi<NotificationHistory> process(Event event, List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return Multi.createFrom().empty();
        } else {
            Action action = event.getAction();
            final EmailTemplate template = emailTemplateFactory.get(action.getBundle(), action.getApplication());
            final boolean shouldSaveAggregation = Arrays.stream(EmailSubscriptionType.values())
                    .filter(emailSubscriptionType -> emailSubscriptionType != EmailSubscriptionType.INSTANT)
                    .anyMatch(emailSubscriptionType -> template.isSupported(action.getEventType(), emailSubscriptionType));

            Uni<Boolean> processUni;

            if (shouldSaveAggregation) {
                EmailAggregation aggregation = new EmailAggregation();
                aggregation.setAccountId(action.getAccountId());
                aggregation.setApplicationName(action.getApplication());
                aggregation.setBundleName(action.getBundle());

                processUni = baseTransformer.transform(action)
                        .onItem().transform(transformedAction -> {
                            aggregation.setPayload(transformedAction);
                            return aggregation;
                        })
                        .onItem().transformToUni(emailAggregation -> this.emailAggregationResources.addEmailAggregation(emailAggregation));
            } else {
                processUni = Uni.createFrom().item(false);
            }

            return processUni.onItem().transformToMulti(_unused -> sendEmail(
                    event,
                    Set.copyOf(endpoints),
                    EmailSubscriptionType.INSTANT,
                    template
            ));
        }
    }

    private Multi<NotificationHistory> sendEmail(Event event, Set<Endpoint> endpoints, EmailSubscriptionType emailSubscriptionType, EmailTemplate emailTemplate) {
        processedCount.increment();
        Action action = event.getAction();
        if (!emailTemplate.isSupported(action.getEventType(), emailSubscriptionType)) {
            return Multi.createFrom().empty();
        }

        TemplateInstance subject = emailTemplate.getTitle(action.getEventType(), emailSubscriptionType);
        TemplateInstance body = emailTemplate.getBody(action.getEventType(), emailSubscriptionType);

        if (subject == null || body == null) {
            return Multi.createFrom().empty();
        }

        return subscriptionResources
                .getEmailSubscribersUserId(action.getAccountId(), action.getBundle(), action.getApplication(), emailSubscriptionType)
                .onItem().transform(Set::copyOf)
                .onItem().transformToUni(subscribers -> recipientResolver.recipientUsers(action.getAccountId(), endpoints, subscribers))
        .onItem().transformToMulti(Multi.createFrom()::iterable)
        .onItem().transformToUniAndConcatenate(user -> emailSender.sendEmail(user, event, subject, body));
    }

    @Incoming(AGGREGATION_CHANNEL)
    @Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
    public Uni<Void> consumeEmailAggregations(String aggregationCommandJson) {
        AggregationCommand aggregationCommand;
        try {
            aggregationCommand = objectMapper.readValue(aggregationCommandJson, AggregationCommand.class);
        } catch (JsonProcessingException e) {
            log.log(Level.SEVERE, "Kafka aggregation payload parsing failed", e);
            return Uni.createFrom().nullItem();
        }

        return sessionFactory.withStatelessSession(statelessSession -> {
            return processAggregateEmailsByAggregationKey(
                    aggregationCommand.getAggregationKey(),
                    aggregationCommand.getStart(),
                    aggregationCommand.getEnd(),
                    aggregationCommand.getSubscriptionType(),
                    // Delete on daily
                    aggregationCommand.getSubscriptionType().equals(EmailSubscriptionType.DAILY)
            ).onItem().ignoreAsUni();
        });
    }

    private Multi<Tuple2<NotificationHistory, EmailAggregationKey>> processAggregateEmailsByAggregationKey(EmailAggregationKey aggregationKey, LocalDateTime startTime, LocalDateTime endTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {
        final EmailTemplate emailTemplate = emailTemplateFactory.get(aggregationKey.getBundle(), aggregationKey.getApplication());

        Multi<Tuple2<NotificationHistory, EmailAggregationKey>> doDelete = delete ?
                emailAggregationResources.purgeOldAggregation(aggregationKey, endTime)
                        .onItem().transformToMulti(unused -> Multi.createFrom().empty()) :
                Multi.createFrom().empty();

        if (!emailTemplate.isEmailSubscriptionSupported(emailSubscriptionType)) {
            return doDelete;
        }

        TemplateInstance subject = emailTemplate.getTitle(null, emailSubscriptionType);
        TemplateInstance body = emailTemplate.getBody(null, emailSubscriptionType);

        if (subject == null || body == null) {
            return doDelete;
        }
        return emailAggregator.getAggregated(aggregationKey, emailSubscriptionType, startTime, endTime)
                .onItem().transform(Map::entrySet)
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transformToMultiAndConcatenate(entries -> {

                    Action action = new Action();
                    action.setContext(entries.getValue());
                    action.setEvents(List.of());
                    action.setAccountId(aggregationKey.getAccountId());
                    action.setApplication(aggregationKey.getApplication());
                    action.setBundle(aggregationKey.getBundle());

                    // We don't have a eventtype as this aggregates over multiple event types
                    action.setEventType(null);
                    action.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

                    Event event = new Event();
                    event.setAction(action);

                    return emailSender.sendEmail(entries.getKey(), event, subject, body)
                            .onItem().transformToMulti(notificationHistory -> Multi.createFrom().item(Tuple2.of(notificationHistory, aggregationKey)));
                })
                .onTermination().call((throwable, aBoolean) -> {
                    if (throwable != null) {
                        log.log(Level.WARNING, "Error while processing aggregation", throwable);
                    }
                    return doDelete.toUni();
                });
    }
}
