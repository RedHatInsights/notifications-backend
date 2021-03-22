package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.aggregators.AbstractEmailPayloadAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.EmailPayloadAggregatorFactory;
import com.redhat.cloud.notifications.processors.email.bop.Email;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.templates.AbstractEmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailSubscriptionTypeProcessor implements EndpointTypeProcessor {

    private final Logger log = Logger.getLogger(this.getClass().getName());
    private final ZoneId zoneId = ZoneId.systemDefault();

    static final String BOP_APITOKEN_HEADER = "x-rh-apitoken";
    static final String BOP_CLIENT_ID_HEADER = "x-rh-clientid";
    static final String BOP_ENV_HEADER = "x-rh-insights-env";

    static final String BODY_TYPE_HTML = "html";

    @Inject
    Vertx vertx;

    @Inject
    WebhookTypeProcessor webhookSender;

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @Inject
    EmailAggregationResources emailAggregationResources;

    @ConfigProperty(name = "processor.email.bop_url")
    String bopUrl;

    @ConfigProperty(name = "processor.email.bop_apitoken")
    String bopApiToken;

    @ConfigProperty(name = "processor.email.bop_client_id")
    String bopClientId;

    @ConfigProperty(name = "processor.email.bop_env")
    String bopEnv;

    @ConfigProperty(name = "processor.email.no_reply")
    String noReplyAddress;

    protected HttpRequest<Buffer> buildBOPHttpRequest() {
        WebClientOptions options = new WebClientOptions()
                .setTrustAll(true)
                .setConnectTimeout(3000);

        return WebClient.create(vertx, options)
                .postAbs(bopUrl)
                .putHeader(BOP_APITOKEN_HEADER, bopApiToken)
                .putHeader(BOP_CLIENT_ID_HEADER, bopClientId)
                .putHeader(BOP_ENV_HEADER, bopEnv);
    }

    protected Email buildEmail(Set<String> recipients) {
        Email email = new Email();
        email.setBodyType(BODY_TYPE_HTML);
        email.setCcList(Set.of());
        email.setRecipients(Set.of(noReplyAddress));
        email.setBccList(recipients);
        return email;
    }

    static class Emails {
        @JsonProperty("emails")
        private Set<Email> emails;

        Emails() {
            emails = new HashSet<>();
        }

        public void addEmail(Email email) {
            emails.add(email);
        }

        public Set<Email> getEmails() {
            return emails;
        }
    }


    @Override
    public Uni<NotificationHistory> process(Notification item) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setAccountId(item.getAction().getAccountId());
        aggregation.setApplication(item.getAction().getApplication());
        aggregation.setBundle(item.getAction().getBundle());
        aggregation.setPayload(JsonObject.mapFrom(item.getAction().getPayload()));

        final AbstractEmailTemplate template = EmailTemplateFactory.get(item.getAction().getBundle(), item.getAction().getApplication());
        final boolean shouldSaveAggregation = Arrays.asList(EmailSubscriptionType.values())
                .stream()
                .filter(emailSubscriptionType -> emailSubscriptionType != EmailSubscriptionType.INSTANT)
                .anyMatch(emailSubscriptionType -> template.isSupported(item.getAction().getEventType(), emailSubscriptionType));

        if (shouldSaveAggregation) {
            return this.emailAggregationResources.addEmailAggregation(aggregation)
                    .onItem().transformToUni(aBoolean -> sendEmail(item, EmailSubscriptionType.INSTANT));
        }

        return sendEmail(item, EmailSubscriptionType.INSTANT);
    }

    private Uni<NotificationHistory> sendEmail(Notification item, EmailSubscriptionType emailSubscriptionType) {
        final HttpRequest<Buffer> bopRequest = this.buildBOPHttpRequest();

        return this.subscriptionResources.getEmailSubscribers(item.getTenant(), item.getAction().getBundle(), item.getAction().getApplication(), emailSubscriptionType)
            .onItem().transform(emailSubscription -> emailSubscription.getUsername())
                .collectItems().with(Collectors.toSet())
                .onItem().transform(userSet -> {
                    if (userSet.size() > 0) {
                        return this.buildEmail(userSet);
                    }

                    return null;
                })
                .onItem().transformToUni(email -> {
                    if (email == null) {
                        return Uni.createFrom().nullItem();
                    }

                    AbstractEmailTemplate emailTemplate = EmailTemplateFactory.get(item.getAction().getBundle(), item.getAction().getApplication());

                    if (emailTemplate.isSupported(item.getAction().getEventType(), emailSubscriptionType)) {
                        Uni<String> title = emailTemplate.getTitle(item.getAction().getEventType(), emailSubscriptionType)
                                .data("payload", item.getAction().getPayload())
                                .createMulti()
                                .collectItems().with(Collectors.joining())
                                .onFailure()
                                .recoverWithItem(templateEx -> {
                                    log.warning(
                                            String.format(
                                                    "Unable to render template title for application: [%s], eventType: [%s], subscriptionType: [%s]. Error: %s",
                                                    item.getAction().getApplication(),
                                                    item.getAction().getEventType(),
                                                    emailSubscriptionType,
                                                    templateEx.getMessage()
                                            )
                                    );
                                    return null;
                                });

                        Uni<String> body = emailTemplate.getBody(item.getAction().getEventType(), emailSubscriptionType)
                                .data("payload", item.getAction().getPayload())
                                .createMulti()
                                .collectItems().with(Collectors.joining())
                                .onFailure()
                                .recoverWithItem(templateEx -> {
                                    log.warning(
                                            String.format(
                                                    "Unable to render template body for application: [%s], eventType: [%s], subscriptionType: [%s]. Error: %s",
                                                    item.getAction().getApplication(),
                                                    item.getAction().getEventType(),
                                                    emailSubscriptionType,
                                                    templateEx.getMessage()
                                            )
                                    );
                                    return null;
                                });

                        return Uni.combine().all()
                                .unis(
                                        Uni.createFrom().item(email),
                                        title,
                                        body
                                ).asTuple()
                                .onItem().transform(objects -> {
                                    if (objects == null || objects.getItem1() == null || objects.getItem2() == null || objects.getItem3() == null) {
                                        return null;
                                    }

                                    return objects;
                                });
                    }

                    return Uni.createFrom().nullItem();
                })
                .onItem().transform(data -> {
                    if (data != null) {
                        Email email = data.getItem1();
                        String title = data.getItem2();
                        String body = data.getItem3();
                        email.setSubject(title);
                        email.setBody(body);

                        return email;
                    }

                    return null;
                })
                .onItem().transformToUni(email -> {
                    if (email == null) {
                        return Uni.createFrom().nullItem();
                    }

                    Emails emails = new Emails();
                    emails.addEmail(email);
                    Uni<JsonObject> payload = Uni.createFrom().item(JsonObject.mapFrom(emails));

                    // TODO Add recipients processing from policies-notifications processing (failed recipients)
                    //      by checking the NotificationHistory's details section (if missing payload - fix in WebhookTypeProcessor)

                    // TODO If the call fails - we should probably rollback Kafka topic (if BOP is down for example)
                    //      also add metrics for these failures
                    return webhookSender.doHttpRequest(item, bopRequest, payload);
                });
    }


    private Multi<Tuple2<NotificationHistory, EmailAggregationKey>> processAggregateEmailsByAggregationKey(EmailAggregationKey aggregationKey, LocalDateTime startTime, LocalDateTime endTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {

        return subscriptionResources.getEmailSubscribersCount(aggregationKey.getAccountId(), aggregationKey.getBundle(), aggregationKey.getApplication(), emailSubscriptionType)
                .onItem().transformToMulti(subscriberCount -> {
                    AbstractEmailPayloadAggregator aggregator = EmailPayloadAggregatorFactory.by(aggregationKey);

                    if (subscriberCount > 0 && aggregator != null) {
                        return emailAggregationResources.getEmailAggregation(aggregationKey, startTime, endTime)
                                .collectItems().in(() -> aggregator, AbstractEmailPayloadAggregator::aggregate).toMulti();
                    }

                    if (delete) {
                        // Nothing to do, delete them right away.
                        return emailAggregationResources.purgeOldAggregation(aggregationKey, endTime)
                                .toMulti()
                                .onItem().transformToMultiAndMerge(i -> Multi.createFrom().empty());
                    }

                    return Multi.createFrom().empty();
                })
                .onItem().transformToMulti(aggregator -> {
                    String accountId = aggregationKey.getAccountId();
                    String bundle = aggregationKey.getBundle();
                    String application = aggregationKey.getApplication();

                    if (aggregator.getProcessedAggregations() == 0) {
                        return Multi.createFrom().empty();
                    }

                    aggregator.setStartTime(startTime);
                    aggregator.setEndTimeKey(endTime);
                    Action action = new Action();
                    action.setPayload(aggregator.getPayload());
                    action.setAccountId(accountId);
                    action.setApplication(application);
                    action.setBundle(bundle);

                    // We don't have a eventtype, this aggregates over multiple event types
                    action.setEventType(null);
                    action.setTimestamp(LocalDateTime.now());

                    // We don't have any endpoint as this aggregates multiple endpoints
                    Notification item = new Notification(action, null);

                    return sendEmail(item, emailSubscriptionType).onItem().transformToMulti(notificationHistory -> Multi.createFrom().item(Tuple2.of(notificationHistory, aggregationKey)));
                }).merge()
                .onItem().transformToMulti(result -> {
                    if (delete) {
                        return emailAggregationResources.purgeOldAggregation(aggregationKey, endTime)
                                .toMulti()
                                .onItem().transform(integer -> result);
                    }

                    return Multi.createFrom().item(result);
                }).merge();
                // Todo: If we want to save the NotificationHistory, this could be a good place to do so. We would probably require a special EndpointType
                // .onItem().invoke(result -> { })
    }

    public Uni<List<Tuple2<NotificationHistory, EmailAggregationKey>>> processAggregateEmails(Instant scheduledFireTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {
        Instant yesterdayScheduledFireTime = scheduledFireTime.minus(emailSubscriptionType.getDuration());

        LocalDateTime endTime = LocalDateTime.ofInstant(scheduledFireTime, zoneId);
        LocalDateTime startTime = LocalDateTime.ofInstant(yesterdayScheduledFireTime, zoneId);
        final LocalDateTime aggregateStarted = LocalDateTime.now();

        log.info(String.format("Running %s email aggregation for period (%s, %s)", emailSubscriptionType.toString(), startTime.toString(), endTime.toString()));

        return emailAggregationResources.getApplicationsWithPendingAggregation(startTime, endTime)
                .onItem().transformToMulti(aggregationKey -> processAggregateEmailsByAggregationKey(aggregationKey, startTime, endTime, emailSubscriptionType, delete))
                .merge().collectItems().asList()
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
