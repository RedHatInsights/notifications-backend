package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.bop.Email;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.templates.Policies;
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
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailSubscriptionTypeProcessor implements EndpointTypeProcessor {

    static final String BOP_APITOKEN_HEADER = "x-rh-apitoken";
    static final String BOP_CLIENT_ID_HEADER = "x-rh-clientid";
    static final String BOP_ENV_HEADER = "x-rh-insights-env";

    static final String BODY_TYPE_HTML = "html";

    @Inject
    Vertx vertx;

    @Inject
    WebhookTypeProcessor webhookSender;

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
                .setSsl(false)
                .setConnectTimeout(3000);

        return WebClient.create(vertx, options)
                .rawAbs("POST", bopUrl)
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

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @Override
    public Uni<NotificationHistory> process(Notification item) {
        final String accountId = item.getTenant();
        final HttpRequest<Buffer> bobRequest = this.buildBOPHttpRequest();

        return this.subscriptionResources.getEmailSubscribers(accountId, EmailSubscriptionType.INSTANT)
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
                            .data("tags", item.getAction().getTags())
                            .data("params", item.getAction().getParams())
                            .data("timestamp", item.getAction().getTimestamp())
                            .createMulti().collectItems().with(Collectors.joining());

                    Uni<String> body = Policies.Templates
                            .instantEmailBody()
                            .data("tags", item.getAction().getTags())
                            .data("params", item.getAction().getParams())
                            .data("timestamp", item.getAction().getTimestamp())
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
                    Emails emails = new Emails();
                    emails.addEmail(email);
                    Uni<JsonObject> payload = Uni.createFrom().item(JsonObject.mapFrom(emails));

                    // TODO Add recipients processing from policies-notifications processing (failed recipients)
                    //      by checking the NotificationHistory's details section (if missing payload - fix in WebhookTypeProcessor)

                    // TODO If the call fails - we should probably rollback Kafka topic (if BOP is down for example)
                    //      also add metrics for these failures
                    return webhookSender.doHttpRequest(item, bobRequest, payload);
                });
    }

}
