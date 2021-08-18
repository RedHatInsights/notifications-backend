package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.webclient.BopWebClient;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.EmailTemplateService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class EmailSender {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    static final String BOP_APITOKEN_HEADER = "x-rh-apitoken";
    static final String BOP_CLIENT_ID_HEADER = "x-rh-clientid";
    static final String BOP_ENV_HEADER = "x-rh-insights-env";
    static final String BODY_TYPE_HTML = "html";
    static final ZoneOffset UTC = ZoneOffset.UTC;

    @Inject
    @BopWebClient
    WebClient bopWebClient;

    @ConfigProperty(name = "processor.email.bop_url")
    String bopUrl;

    @ConfigProperty(name = "processor.email.bop_apitoken")
    String bopApiToken;

    @ConfigProperty(name = "processor.email.bop_client_id")
    String bopClientId;

    @ConfigProperty(name = "processor.email.bop_env")
    String bopEnv;

    @Inject
    WebhookTypeProcessor webhookSender;

    @Inject
    EndpointResources endpointResources;

    @Inject
    EmailTemplateService emailTemplateService;

    @Inject
    MeterRegistry registry;

    private Counter processedCount;
    private Timer processTime;

    @PostConstruct
    public void init() {
        processedCount = registry.counter("processor.email.processed");
        processTime = registry.timer("processor.email.process-time");
    }

    public Uni<NotificationHistory> sendEmail(User user, Action action, TemplateInstance subject, TemplateInstance body) {
        final HttpRequest<Buffer> bopRequest = this.buildBOPHttpRequest();
        LocalDateTime start = LocalDateTime.now(UTC);

        return endpointResources.getOrCreateEmailSubscriptionEndpoint(action.getAccountId(), new EmailSubscriptionProperties())
                .onItem().transformToUni(endpoint -> {
                    Notification notification = new Notification(action, endpoint);

                    // TODO Add recipients processing from policies-notifications processing (failed recipients)
                    //      by checking the NotificationHistory's details section (if missing payload - fix in WebhookTypeProcessor)

                    // TODO If the call fails - we should probably rollback Kafka topic (if BOP is down for example)
                    //      also add metrics for these failures

                    return webhookSender.doHttpRequest(
                            notification,
                            bopRequest,
                            getPayload(user, action, subject, body)
                    ).onItem().invoke(unused -> {
                        processedCount.increment();
                        processTime.record(Duration.between(start, LocalDateTime.now(UTC)));
                    });
                }).onFailure().recoverWithNull();
    }

    private Uni<JsonObject> getPayload(User user, Action action, TemplateInstance subject, TemplateInstance body) {
        return Uni.combine().all().unis(
                emailTemplateService.renderTemplate(user, action, subject),
                emailTemplateService.renderTemplate(user, action, body)
        )
                .asTuple()
                .onItem().transform(rendered -> {
                    Emails emails = new Emails();
                    emails.addEmail(buildEmail(
                            user.getUsername(),
                            rendered.getItem1(),
                            rendered.getItem2()
                    ));
                    return JsonObject.mapFrom(emails);
                });
    }

    protected HttpRequest<Buffer> buildBOPHttpRequest() {
        return bopWebClient
                .postAbs(bopUrl)
                .putHeader(BOP_APITOKEN_HEADER, bopApiToken)
                .putHeader(BOP_CLIENT_ID_HEADER, bopClientId)
                .putHeader(BOP_ENV_HEADER, bopEnv);
    }

    protected Email buildEmail(String recipient, String subject, String body) {
        Email email = new Email();
        email.setBodyType(BODY_TYPE_HTML);
        email.setRecipients(Set.of(recipient));
        email.setSubject(subject);
        email.setBody(body);
        return email;
    }
}
