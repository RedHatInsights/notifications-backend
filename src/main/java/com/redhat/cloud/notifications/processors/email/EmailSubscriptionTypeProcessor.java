package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.bop.Email;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.templates.Policies;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailSubscriptionTypeProcessor implements EndpointTypeProcessor {

    private final Logger log = Logger.getLogger(this.getClass().getName());

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
        final String accountId = item.getTenant();

        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setAccountId(item.getAction().getAccountId());
        aggregation.setApplication(item.getAction().getApplication());
        aggregation.setPayload(JsonObject.mapFrom(item.getAction().getPayload()));

        return this.emailAggregationResources.addEmailAggregation(aggregation)
                .onItem().ignore()
                .andSwitchTo(sendEmail(item, EmailSubscriptionType.INSTANT));
    }

    private Uni<NotificationHistory> sendEmail(Notification item, EmailSubscriptionType emailSubscriptionType) {
        final HttpRequest<Buffer> bopRequest = this.buildBOPHttpRequest();

        this.subscriptionResources.getEmailSubscribers(item.getTenant(), emailSubscriptionType)
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

                    Uni<String> title = Policies.Templates
                            .instantEmailTitle()
                            .data("payload", item.getAction().getPayload())
                            .createMulti().collectItems().with(Collectors.joining());

                    Uni<String> body = Policies.Templates
                            .instantEmailBody()
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


    @Scheduled(identity = "dailyEmailProcessor", every = "10s")
    public void processDailyEmail(ScheduledExecution se) {
        Instant scheduledFireTime = se.getScheduledFireTime();
        Instant yesterdayScheduledFireTime = scheduledFireTime.minus(Duration.ofDays(1));

        System.out.println("hello world");
    }

}
