package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.events.EventWrapper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.processors.webclient.BopWebClient;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.utils.LineBreakCleaner;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
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
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailSender {

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

    @ConfigProperty(name = "processor.email.no_reply")
    String noReplyEmail;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    WebhookTypeProcessor webhookSender;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    TemplateService templateService;

    @Inject
    MeterRegistry registry;

    private Timer processTime;

    @PostConstruct
    public void init() {
        processTime = registry.timer("processor.email.process-time");
        /*
         * The token value we receive contains a line break because of the standard mime encryption. Gabor Burges tried
         * to remove it but that didn't work, so we have to do it here because Vert.x 4 does not allow line breaks in
         * HTTP headers.
         */
        bopApiToken = LineBreakCleaner.clean(bopApiToken);
    }

    public void sendEmail(Set<User> users, Event event, TemplateInstance subject, TemplateInstance body, boolean persistHistory) {
        if (users.isEmpty()) {
            Log.debug("No recipient found for this email");
            return;
        }
        final HttpRequest<Buffer> bopRequest = this.buildBOPHttpRequest();
        LocalDateTime start = LocalDateTime.now(UTC);

        Timer.Sample processedTimer = Timer.start(registry);

        EventType eventType = event.getEventType();
        String bundleName = "NA";
        String applicationName = "NA";
        if (eventType != null) {
            bundleName = eventType.getApplication().getBundle().getName();
            applicationName = eventType.getApplication().getName();
        } else if (event.getEventWrapper().getEvent() instanceof Action action) {
            bundleName = action.getBundle();
            applicationName = action.getApplication();
        }

        // uses canonical EmailSubscription
        try {
            Endpoint endpoint = endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION);

            // TODO Add recipients processing from policies-notifications processing (failed recipients)
            //      by checking the NotificationHistory's details section (if missing payload - fix in WebhookTypeProcessor)

            // TODO If the call fails - we should probably rollback Kafka topic (if BOP is down for example)
            //      also add metrics for these failures

            webhookSender.doHttpRequest(
                event, endpoint,
                bopRequest,
                getPayload(users, event.getEventWrapper(), subject, body),
                "POST",
                bopUrl,
                persistHistory);
        } catch (Exception e) {
            Log.error("Email sending failed", e);
        } finally {
            processedTimer.stop(registry.timer("processor.email.processed", "bundle", bundleName, "application", applicationName));
            processTime.record(Duration.between(start, LocalDateTime.now(UTC)));
        }
    }

    @Deprecated(forRemoval = true) // one email should be able to be send to multiple users because its body must not contains user personal data anymore
    public void sendEmail(User user, Event event, TemplateInstance subject, TemplateInstance body, boolean persistHistory) {
        final HttpRequest<Buffer> bopRequest = this.buildBOPHttpRequest();
        LocalDateTime start = LocalDateTime.now(UTC);

        Timer.Sample processedTimer = Timer.start(registry);

        EventType eventType = event.getEventType();
        String bundleName = "NA";
        String applicationName = "NA";
        if (eventType != null) {
            bundleName = eventType.getApplication().getBundle().getName();
            applicationName = eventType.getApplication().getName();
        } else if (event.getEventWrapper().getEvent() instanceof Action action) {
            bundleName = action.getBundle();
            applicationName = action.getApplication();
        }

        // uses canonical EmailSubscription
        try {
            Endpoint endpoint = endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION);

            // TODO Add recipients processing from policies-notifications processing (failed recipients)
            //      by checking the NotificationHistory's details section (if missing payload - fix in WebhookTypeProcessor)

            // TODO If the call fails - we should probably rollback Kafka topic (if BOP is down for example)
            //      also add metrics for these failures

            webhookSender.doHttpRequest(
                    event, endpoint,
                    bopRequest,
                    getPayload(user, event.getEventWrapper(), subject, body), "POST", bopUrl, persistHistory);

            processedTimer.stop(registry.timer("processor.email.processed", "bundle", bundleName, "application", applicationName));

            processTime.record(Duration.between(start, LocalDateTime.now(UTC)));
        } catch (Exception e) {
            Log.info("Email sending failed", e);
        }
    }

    private JsonObject getPayload(Set<User> users, EventWrapper<?, ?> eventWrapper, TemplateInstance subject, TemplateInstance body) {

        String renderedSubject;
        String renderedBody;
        try {
            renderedSubject = templateService.renderTemplate(eventWrapper.getEvent(), subject);
            renderedBody = templateService.renderTemplate(eventWrapper.getEvent(), body);
        } catch (Exception e) {
            Log.warnf(e,
                "Unable to render template for %s.",
                eventWrapper.getKey().toString()
            );
            throw e;
        }
        Emails emails = new Emails();
        emails.addEmail(buildEmail(
            users,
            renderedSubject,
            renderedBody
        ));
        return JsonObject.mapFrom(emails);
    }

    @Deprecated(forRemoval = true)
    private JsonObject getPayload(User user, EventWrapper<?, ?> eventWrapper, TemplateInstance subject, TemplateInstance body) {

        String renderedSubject;
        String renderedBody;
        try {
            renderedSubject = templateService.renderTemplate(user, eventWrapper.getEvent(), subject);
            renderedBody = templateService.renderTemplate(user, eventWrapper.getEvent(), body);
        } catch (Exception e) {
            Log.warnf(e,
                    "Unable to render template for %s.",
                    eventWrapper.getKey().toString()
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

    @Deprecated(forRemoval = true)
    protected Email buildEmail(String recipient, String subject, String body) {
        Email email = new Email();
        email.setBodyType(BODY_TYPE_HTML);
        email.setRecipients(Set.of(recipient));
        email.setSubject(subject);
        email.setBody(body);
        return email;
    }

    protected Email buildEmail(Set<User> recipients, String subject, String body) {
        Set<String> usersEmail = recipients.stream().map(User::getUsername).collect(Collectors.toSet());
        Email email = new Email();
        email.setBodyType(BODY_TYPE_HTML);
        if (featureFlipper.isAddDefaultRecipientOnSingleEmail()) {
            email.setRecipients(Set.of(noReplyEmail));
        }
        email.setBccList(usersEmail);
        email.setSubject(subject);
        email.setBody(body);
        return email;
    }
}
