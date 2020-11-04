package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.bop.Email;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
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

@ApplicationScoped
public class EmailTypeProcessor implements EndpointTypeProcessor {

    private static final String BOP_APITOKEN_HEADER = "x-rh-apitoken";
    private static final String BOP_CLIENT_ID_HEADER = "x-rh-clientid";
    private static final String BOP_ENV_HEADER = "x-rh-insights-env";

    @Inject
    Vertx vertx;

    @Inject
    WebhookTypeProcessor webhook;

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

    @Override
    public Uni<NotificationHistory> process(Notification item) {
        WebClientOptions options = new WebClientOptions()
                .setSsl(false)
                .setConnectTimeout(3000); // TODO Should this be configurable by the system? We need a maximum in any case

        final HttpRequest<Buffer> req = WebClient.create(vertx, options)
                .post(bopUrl)
                .putHeader(BOP_APITOKEN_HEADER, bopApiToken)
                .putHeader(BOP_CLIENT_ID_HEADER, bopClientId)
                .putHeader(BOP_ENV_HEADER, bopEnv);

        Emails emails = new Emails();

        // TODO Implement BOPTransformer (payload, topic creation, recipients handling, etc..)
        Uni<JsonObject> payload = Uni.createFrom().item(JsonObject.mapFrom(emails));

        // TODO Add recipients processing from policies-notifications processing
        return webhook.doHttpRequest(item, req, payload);
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
    }
}
