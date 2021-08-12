package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
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
import org.eclipse.microprofile.config.ConfigProvider;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;


import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.values;
import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class OldDailyEmailCronJob implements EndpointTypeProcessor {

    private final Logger log = Logger.getLogger(this.getClass().getName());

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @Inject
    RecipientResolver recipientResolver;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    EmailTemplateFactory emailTemplateFactory;

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Inject
    EmailSender emailSender;

    @Inject
    EmailSubscriptionTypeProcessor emailSubscriptionTypeProcessor;

    @Override
    public Multi<NotificationHistory> process(Action action, List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return Multi.createFrom().empty();
        }
        final EmailTemplate template = emailTemplateFactory.get(action.getBundle(), action.getApplication());
        final boolean saveAggregation = Arrays.stream(values())
                .filter(emailSubscriptionType -> emailSubscriptionType != INSTANT)
                .anyMatch(emailSubscriptionType -> template.isSupported(action.getEventType(), emailSubscriptionType));

        Uni<Boolean> processUni;

        if (saveAggregation) {
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

        return processUni.onItem().transformToMulti(_unused -> sendInstantEmail(
                action,
                Set.copyOf(endpoints),
                template
        ));
    }

    private Multi<NotificationHistory> sendInstantEmail(Action action, Set<Endpoint> endpoints, EmailTemplate emailTemplate) {
        if (!emailTemplate.isSupported(action.getEventType(), INSTANT)) {
            return Multi.createFrom().empty();
        }

        TemplateInstance subject = emailTemplate.getTitle(action.getEventType(), INSTANT);
        TemplateInstance body = emailTemplate.getBody(action.getEventType(), INSTANT);

        if (subject == null || body == null) {
            return Multi.createFrom().empty();
        }

        return subscriptionResources
                .getEmailSubscribersUserId(action.getAccountId(), action.getBundle(), action.getApplication(), INSTANT)
                .onItem().transform(Set::copyOf)
                .onItem().transformToUni(subscribers -> recipientResolver.recipientUsers(action.getAccountId(), endpoints, subscribers))
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transformToUniAndConcatenate(user -> emailSender.sendEmail(user, action, subject, body));
    }

    @Scheduled(identity = "dailyEmailProcessor", cron = "{notifications.backend.email.subscription.daily.cron}")
    public void processDailyEmail(ScheduledExecution se) {
        // Only delete on the largest aggregate time frame. Currently daily.
        if (isScheduleEnabled()) {
            processAggregateEmails(se.getScheduledFireTime()).await().indefinitely();
        }
    }

    private boolean isScheduleEnabled() {
        // The scheduled job is enabled by default.
        return ConfigProvider.getConfig().getOptionalValue("notifications.backend.email.subscription.periodic.cron.enabled", Boolean.class).orElse(true);
    }

    private Uni<List<Tuple2<NotificationHistory, EmailAggregationKey>>> processAggregateEmails(Instant scheduledFireTime) {
        Instant yesterdayScheduledFireTime = scheduledFireTime.minus(DAILY.getDuration());

        LocalDateTime endTime = LocalDateTime.ofInstant(scheduledFireTime, UTC);
        LocalDateTime startTime = LocalDateTime.ofInstant(yesterdayScheduledFireTime, UTC);
        final LocalDateTime aggregateStarted = LocalDateTime.now();

        log.info(String.format("Running %s email aggregation for period (%s, %s)", DAILY, startTime, endTime));

        return emailAggregationResources.getApplicationsWithPendingAggregation(startTime, endTime)
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transformToMultiAndConcatenate(aggregationKey -> emailSubscriptionTypeProcessor.processAggregateEmailsByAggregationKey(aggregationKey, startTime, endTime, DAILY, true))
                .collect().asList()
                .onItem().invoke(result -> {
                    final LocalDateTime aggregateFinished = LocalDateTime.now();
                    log.info(
                            String.format(
                                    "Finished running %s email aggregation for period (%s, %s) after %d seconds. %d (accountIds, applications) pairs were processed",
                                    DAILY,
                                    startTime,
                                    endTime,
                                    ChronoUnit.SECONDS.between(aggregateStarted, aggregateFinished),
                                    result.size()
                            )
                    );
                });
    }
}
