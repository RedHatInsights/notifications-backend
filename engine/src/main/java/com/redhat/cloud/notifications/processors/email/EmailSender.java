package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.webclient.BopWebClient;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.utils.LineBreakCleaner;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class EmailSender {

    private static final Logger logger = Logger.getLogger(EmailSender.class);

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
    EndpointRepository endpointRepository;

    @Inject
    TemplateService templateService;

    @Inject
    MeterRegistry registry;

    private Counter.Builder processedCount;
    private Timer processTime;

    @PostConstruct
    public void init() {
        processedCount = Counter.builder("processor.email.processed");
        processTime = registry.timer("processor.email.process-time");
        /*
         * The token value we receive contains a line break because of the standard mime encryption. Gabor Burges tried
         * to remove it but that didn't work, so we have to do it here because Vert.x 4 does not allow line breaks in
         * HTTP headers.
         */
        bopApiToken = LineBreakCleaner.clean(bopApiToken);
    }

    public Optional<NotificationHistory> sendEmail(User user, Event event, TemplateInstance subject, TemplateInstance body) {
        final HttpRequest<Buffer> bopRequest = this.buildBOPHttpRequest();
        LocalDateTime start = LocalDateTime.now(UTC);

        Action action = event.getAction();
        // uses canonical EmailSubscription
        try {
            Endpoint endpoint = endpointRepository.getOrCreateDefaultEmailSubscription(action.getAccountId());
            Notification notification = new Notification(event, endpoint);

            // TODO Add recipients processing from policies-notifications processing (failed recipients)
            //      by checking the NotificationHistory's details section (if missing payload - fix in WebhookTypeProcessor)

            // TODO If the call fails - we should probably rollback Kafka topic (if BOP is down for example)
            //      also add metrics for these failures

            NotificationHistory history = webhookSender.doHttpRequest(
                    notification,
                    bopRequest,
                    getPayload(user, action, subject, body));

            processedCount.tags("bundle", action.getBundle(), "application", action.getApplication());
            processedCount.register(registry).increment();

            processTime.record(Duration.between(start, LocalDateTime.now(UTC)));

            return Optional.of(history);
        } catch (Exception e) {
            logger.info("Email sending failed", e);
            return Optional.empty();
        }
    }

    private JsonObject getPayload(User user, Action action, TemplateInstance subject, TemplateInstance body) {

        String renderedSubject;
        String renderedBody;
        try {
            renderedSubject = templateService.renderTemplate(user, action, subject);
            renderedBody = templateService.renderTemplate(user, action, body);
        } catch (Exception e) {
            logger.warnf(e,
                    "Unable to render template for bundle: [%s] application: [%s], eventType: [%s].",
                    action.getBundle(),
                    action.getApplication(),
                    action.getEventType()
            );
            throw e;
        }
        Emails emails = new Emails();
        emails.addEmail(buildEmail(
                user.getUsername(),
                renderedSubject,
                renderedBody
        ));
        return JsonObject.mapFrom(emails);
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
