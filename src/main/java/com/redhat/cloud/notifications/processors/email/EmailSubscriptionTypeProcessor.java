package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.bop.Email;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.templates.Policies;
import io.quarkus.qute.TemplateInstance;
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
        aggregation.setPayload(JsonObject.mapFrom(item.getAction().getPayload()));

        return this.emailAggregationResources.addEmailAggregation(aggregation)
                .onItem().transformToUni(aBoolean -> sendEmail(item, EmailSubscriptionType.INSTANT));
    }

    private TemplateInstance templateTitleForSubscriptionType(EmailSubscriptionType subscriptionType) {
        switch (subscriptionType) {
            case DAILY:
                return Policies.Templates.dailyEmailTitle();
            case INSTANT:
                return Policies.Templates.instantEmailTitle();
            default:
                throw new IllegalArgumentException("Unknown EmailSubscriptionType:" + subscriptionType);
        }
    }

    private TemplateInstance templateBodyForSubscriptionType(EmailSubscriptionType subscriptionType) {
        switch (subscriptionType) {
            case DAILY:
                return Policies.Templates.dailyEmailBody();
            case INSTANT:
                return Policies.Templates.instantEmailBody();
            default:
                throw new IllegalArgumentException("Unknown EmailSubscriptionType:" + subscriptionType);
        }
    }

    private Uni<NotificationHistory> sendEmail(Notification item, EmailSubscriptionType emailSubscriptionType) {
        final HttpRequest<Buffer> bopRequest = this.buildBOPHttpRequest();

        return this.subscriptionResources.getEmailSubscribers(item.getTenant(), emailSubscriptionType)
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

                    Uni<String> title = templateTitleForSubscriptionType(emailSubscriptionType)
                            .data("payload", item.getAction().getPayload())
                            .createMulti().collectItems().with(Collectors.joining());

                    Uni<String> body = templateBodyForSubscriptionType(emailSubscriptionType)
                            .data("payload", item.getAction().getPayload())
                            .createMulti().collectItems().with(Collectors.joining());

                    return Uni.combine().all()
                            .unis(
                                    Uni.createFrom().item(email),
                                    title,
                                    body
                            ).asTuple();
                })
                .onItem().ifNotNull().transform(data -> {
                    Email email = data.getItem1();
                    String title = data.getItem2();
                    String body = data.getItem3();
                    email.setSubject(title);
                    email.setBody(body);

                    return email;
                })
                .onItem().transformToUni(email -> {
                    if (email == null) {
                        log.fine("No subscribers for type:"  + emailSubscriptionType.toString());
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

    public Uni<List<Tuple2<NotificationHistory, String>>> processAggregateEmails(Instant scheduledFireTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {
        Instant yesterdayScheduledFireTime = scheduledFireTime.minus(emailSubscriptionType.getDuration());

        LocalDateTime endTime = LocalDateTime.ofInstant(scheduledFireTime, zoneId);
        LocalDateTime startTime = LocalDateTime.ofInstant(yesterdayScheduledFireTime, zoneId);
        final LocalDateTime aggregateStarted = LocalDateTime.now();

        log.info(String.format("Running %s email aggregation for period (%s, %s)", emailSubscriptionType.toString(), startTime.toString(), endTime.toString()));

        final String application = "policies";
        // Currently only processing aggregations from policies
        return emailAggregationResources.getAccountIdsWithPendingAggregation(application, startTime, endTime)
                .onItem().transformToUni(accountId ->  Uni.combine().all().unis(
                    Uni.createFrom().item(accountId),
                    subscriptionResources.getEmailSubscribersCount(accountId, emailSubscriptionType)
                ).asTuple()).merge()
                .onItem().transformToMulti(accountAndCount -> {
                    String accountId = accountAndCount.getItem1();

                    if (accountAndCount.getItem2() > 0 || delete) {
                        // Group by accountId
                        return emailAggregationResources.getEmailAggregation(accountId, application, startTime, endTime)
                        .collectItems().in(DailyEmailPayloadAggregator::new, DailyEmailPayloadAggregator::aggregate).toMulti();
                    }

                    return Multi.createFrom().empty();
                }).merge()
                .onItem().transformToMulti(aggregator -> {
                    String accountId = aggregator.getAccountId();
                    if (accountId == null || aggregator.getUniqueHostCount() == 0) {
                        return Multi.createFrom().empty();
                    }

                    aggregator.setStartTime(startTime);
                    aggregator.setEndTimeKey(endTime);
                    Action action = new Action();
                    action.setPayload(aggregator.getPayload());
                    action.setApplication(application);

                    // We don't have a eventtype, this aggregates over multiple event types
                    action.setEventType(null);
                    action.setTimestamp(LocalDateTime.now());
                    action.setAccountId(accountId);

                    // We don't have any endpoint as this aggregates multiple endpoints
                    Notification item = new Notification(action, null);

                    return sendEmail(item, emailSubscriptionType).onItem().transformToMulti(notificationHistory -> Multi.createFrom().item(Tuple2.of(notificationHistory, accountId)));
                }).merge()
                .onItem().transformToMulti(result -> {
                    if (delete) {
                        return emailAggregationResources.purgeOldAggregation(result.getItem2(), application, endTime)
                                .toMulti()
                                .onItem().transform(integer -> result);
                    }

                    return Multi.createFrom().item(result);
                }).merge()
                // Todo: If we want to save the NotificationHistory, this could be a good place to do so. We would probably require a special EndpointType
                // .onItem().invoke(result -> { })
                .collectItems().asList()
                .onItem().invoke(result -> {
                    final LocalDateTime aggregateFinished = LocalDateTime.now();
                    log.info(
                            String.format(
                                    "Finished running %s email aggregation for period (%s, %s) after %d seconds. %d accountIds were processed",
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
