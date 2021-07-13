package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.templates.EmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailSubscriptionTypeProcessor implements EndpointTypeProcessor {

    private final Logger log = Logger.getLogger(this.getClass().getName());
    private final ZoneOffset UTC = ZoneOffset.UTC;

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

    @Override
    public Multi<NotificationHistory> process(Action action, List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return Multi.createFrom().empty();
        } else {
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
                    action,
                    endpoints,
                    EmailSubscriptionType.INSTANT
            ));
        }
    }

    private Multi<NotificationHistory> sendEmail(Action action, List<Endpoint> endpoints, EmailSubscriptionType emailSubscriptionType) {
        final EmailTemplate emailTemplate = emailTemplateFactory.get(action.getBundle(), action.getApplication());
        TemplateInstance subject = emailTemplate.getTitle(action.getEventType(), emailSubscriptionType);
        TemplateInstance body = emailTemplate.getBody(action.getEventType(), emailSubscriptionType);

        if (subject == null || body == null) {
            return Multi.createFrom().empty();
        }

        return subscriptionResources
                .getEmailSubscribers(action.getAccountId(), action.getBundle(), action.getApplication(), emailSubscriptionType)
                .onItem().transformToUni(emailSubscriptions -> {
                    Set<String> subscribers = emailSubscriptions.stream().map(EmailSubscription::getUserId).collect(Collectors.toSet());
                    return recipientResolver.recipientUsers(action.getAccountId(), endpoints, subscribers);
                })
        .onItem().transformToMulti(Multi.createFrom()::iterable)
        .onItem().transformToUniAndConcatenate(user -> emailSender.sendEmail(user, action, subject, body));
    }

    private Multi<Tuple2<NotificationHistory, EmailAggregationKey>> processAggregateEmailsByAggregationKey(EmailAggregationKey aggregationKey, LocalDateTime startTime, LocalDateTime endTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {

        final EmailTemplate emailTemplate = emailTemplateFactory.get(aggregationKey.getBundle(), aggregationKey.getApplication());

        Multi<Tuple2<NotificationHistory, EmailAggregationKey>> doDelete = delete ?
                emailAggregationResources.purgeOldAggregation(aggregationKey, endTime)
                        .onItem().transformToMulti(unused -> Multi.createFrom().empty()) :
                Multi.createFrom().empty();

        if (!emailTemplate.isSupported(null, emailSubscriptionType)) {
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

                    return emailSender.sendEmail(entries.getKey(), action, subject, body)
                            .onItem().transformToMulti(notificationHistory -> Multi.createFrom().item(Tuple2.of(notificationHistory, aggregationKey)));
                })
                .onTermination().call((throwable, aBoolean) -> doDelete.toUni());
    }

    Uni<List<Tuple2<NotificationHistory, EmailAggregationKey>>> processAggregateEmails(Instant scheduledFireTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {
        Instant yesterdayScheduledFireTime = scheduledFireTime.minus(emailSubscriptionType.getDuration());

        LocalDateTime endTime = LocalDateTime.ofInstant(scheduledFireTime, UTC);
        LocalDateTime startTime = LocalDateTime.ofInstant(yesterdayScheduledFireTime, UTC);
        final LocalDateTime aggregateStarted = LocalDateTime.now();

        log.info(String.format("Running %s email aggregation for period (%s, %s)", emailSubscriptionType.toString(), startTime.toString(), endTime.toString()));

        return emailAggregationResources.getApplicationsWithPendingAggregation(startTime, endTime)
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transformToMultiAndConcatenate(aggregationKey -> processAggregateEmailsByAggregationKey(aggregationKey, startTime, endTime, emailSubscriptionType, delete))
                .collect().asList()
                .onItem().invoke(result -> {
                    final LocalDateTime aggregateFinished = LocalDateTime.now();
                    log.info(
                            String.format(
                                    "Finished running %s email aggregation for period (%s, %s) after %d seconds. %d (accountIds, applications) pairs were processed",
                                    emailSubscriptionType.toString(),
                                    startTime.toString(),
                                    endTime.toString(),
                                    ChronoUnit.SECONDS.between(aggregateStarted, aggregateFinished),
                                    result.size()
                            )
                    );
                });
    }

    @Scheduled(identity = "dailyEmailProcessor", cron = "{email.subscription.daily.cron}")
    public void processDailyEmail(ScheduledExecution se) {
        // Only delete on the largest aggregate time frame. Currently daily.
        processAggregateEmails(se.getScheduledFireTime(), EmailSubscriptionType.DAILY, true).await().indefinitely();
    }

}
