package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.models.EmailAttributes;
import com.redhat.cloud.notifications.models.Endpoint;
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

    @Override
    public Uni<NotificationHistory> process(Notification item) {
        WebClientOptions options = new WebClientOptions()
                .setSsl(false)
                .setConnectTimeout(3000);

        final HttpRequest<Buffer> req = WebClient.create(vertx, options)
                .rawAbs("POST", bopUrl)
                .putHeader(BOP_APITOKEN_HEADER, bopApiToken)
                .putHeader(BOP_CLIENT_ID_HEADER, bopClientId)
                .putHeader(BOP_ENV_HEADER, bopEnv);

        // TODO Implement BOPTransformer (payload, topic creation, recipients handling, etc..)
        //      add here both: Endpoint with target email address and email with subscription
        Emails emails = new Emails();

        EmailAttributes attr = (EmailAttributes) item.getEndpoint().getProperties();

        if (attr.getRecipients().size() > 0) {
            Email email = new Email();
            email.setBodyType(BODY_TYPE_HTML);
            email.setCcList(Set.of());
            email.setRecipients(Set.of(noReplyAddress));
            email.setBccList(attr.getRecipients());

            // TODO Add body and subject here
            emails.addEmail(email);
        }

        // TODO Don't send the payload if there's no emails.
        if (emails.getEmails().size() > 0) {
            Uni<JsonObject> payload = Uni.createFrom().item(JsonObject.mapFrom(emails));

            // TODO Add recipients processing from policies-notifications processing (failed recipients)
            //      by checking the NotificationHistory's details section (if missing payload - fix in WebhookTypeProcessor)

            // TODO If the call fails - we should probably rollback Kafka topic (if BOP is down for example)
            //      also add metrics for these failures
            return webhookSender.doHttpRequest(item, req, payload);
        }

        return Uni.createFrom().nullItem();
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
}
