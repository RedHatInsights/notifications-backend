package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.webclient.SslVerificationDisabled;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.templates.EmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple3;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
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
    @SslVerificationDisabled
    WebClient unsecuredWebClient;

    @Inject
    WebhookTypeProcessor webhookSender;

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    EmailTemplateFactory emailTemplateFactory;

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

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Multi<NotificationHistory> process(Action action, List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return Multi.createFrom().empty();
        }
        /*
         * Since EmailSubscriptionProperties is currently empty, if we process all endpoints from the given list,
         * each one of them will be used to send (and possibly aggregate) the exact same email data. This means
         * users will receive duplicate emails for a single event. This can happen when two behavior groups are
         * linked with the same event type and each behavior group contains an EMAIL_SUBSCRIPTION action. We need
         * to prevent duplicate emails which is why only the first endpoint from the given list will be used.
         * TODO: Review this logic if fields are added to EmailSubscriptionProperties.
         */
        Notification notification = new Notification(action, endpoints.get(0));
        return process(notification).toMulti();
    }

    private Uni<NotificationHistory> process(Notification item) {
        final EmailTemplate template = emailTemplateFactory.get(item.getAction().getBundle(), item.getAction().getApplication());
        final boolean shouldSaveAggregation = Arrays.stream(EmailSubscriptionType.values())
                .filter(emailSubscriptionType -> emailSubscriptionType != EmailSubscriptionType.INSTANT)
                .anyMatch(emailSubscriptionType -> template.isSupported(item.getAction().getEventType(), emailSubscriptionType));

        if (shouldSaveAggregation) {
            EmailAggregation aggregation = new EmailAggregation();
            aggregation.setAccountId(item.getAction().getAccountId());
            aggregation.setApplicationName(item.getAction().getApplication());
            aggregation.setBundleName(item.getAction().getBundle());

            return baseTransformer.transform(item.getAction())
                    .onItem().transform(transformedAction -> {
                        aggregation.setPayload(transformedAction);
                        return aggregation;
                    })
                    .onItem().transformToUni(emailAggregation -> this.emailAggregationResources.addEmailAggregation(emailAggregation))
                    .onItem().transformToUni(aBoolean -> sendEmail(item, EmailSubscriptionType.INSTANT));
        }
        return sendEmail(item, EmailSubscriptionType.INSTANT);
    }

    Uni<NotificationHistory> sendEmail(Notification item, EmailSubscriptionType emailSubscriptionType) {
        final HttpRequest<Buffer> bopRequest = unsecuredWebClient
                .postAbs(bopUrl)
                .putHeader(BOP_APITOKEN_HEADER, bopApiToken)
                .putHeader(BOP_CLIENT_ID_HEADER, bopClientId)
                .putHeader(BOP_ENV_HEADER, bopEnv);

        final Action action = item.getAction();
        return this.subscriptionResources.getEmailSubscribers(item.getTenant(), action.getBundle(), action.getApplication(), emailSubscriptionType)
                .onItem().transform(subscriptions -> subscriptions.stream()
                        .map(EmailSubscription::getUserId)
                        .collect(Collectors.toSet()))
                .onItem().transform(recipients -> {
                    if (recipients.size() > 0) {
                        Email email = new Email();
                        email.setBodyType(BODY_TYPE_HTML);
                        email.setCcList(Set.of());
                        email.setRecipients(Set.of(noReplyAddress));
                        email.setBccList(recipients);
                        return email;
                    }
                    return null;
                })
                .onItem().transformToUni(email -> {
                    if (email == null) {
                        return Uni.createFrom().nullItem();
                    }
                    EmailTemplate emailTemplate = emailTemplateFactory.get(action.getBundle(), action.getApplication());
                    if (emailTemplate.isSupported(action.getEventType(), emailSubscriptionType)) {
                        Uni<String> title = renderTitle(item, emailSubscriptionType, emailTemplate);
                        Uni<String> body = renderBody(item, emailSubscriptionType, emailTemplate);
                        return combine(email, title, body);
                    }
                    return Uni.createFrom().nullItem();
                })
                .onItem().transform(triple -> {
                    if (triple != null) {
                        Email email = triple.getItem1();
                        String title = triple.getItem2();
                        String body = triple.getItem3();
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

    private Uni<String> renderBody(Notification item, EmailSubscriptionType emailSubscriptionType, EmailTemplate emailTemplate) {
        return emailTemplate.getBody(item.getAction().getEventType(), emailSubscriptionType)
                .data("action", item.getAction())
                .createMulti()
                .collect().with(Collectors.joining())
                .onFailure()
                .recoverWithItem(templateEx -> {
                    log.log(Level.WARNING, templateEx, () -> String.format(
                            "Unable to render template body for application: [%s], eventType: [%s], subscriptionType: [%s].",
                            item.getAction().getApplication(),
                            item.getAction().getEventType(),
                            emailSubscriptionType
                    ));
                    return null;
                });
    }

    private Uni<String> renderTitle(Notification item, EmailSubscriptionType emailSubscriptionType, EmailTemplate emailTemplate) {
        return emailTemplate.getTitle(item.getAction().getEventType(), emailSubscriptionType)
                .data("action", item.getAction())
                .createMulti()
                .collect().with(Collectors.joining())
                .onFailure()
                .recoverWithItem(templateEx -> {
                    log.log(Level.WARNING, templateEx, () -> String.format(
                            "Unable to render template title for application: [%s], eventType: [%s], subscriptionType: [%s].",
                            item.getAction().getApplication(),
                            item.getAction().getEventType(),
                            emailSubscriptionType
                    ));
                    return null;
                });
    }

    private Uni<Tuple3<Email, String, String>> combine(Email email, Uni<String> title, Uni<String> body) {
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

    @Incoming("aggregation")
    public void consumeEmailAggregations(String aggregationsJson) throws JsonProcessingException {
        List<EmailAggregation> aggregations = objectMapper.readValue(aggregationsJson, new TypeReference<>() { });
        Uni<List<EmailAggregation>> emailaggregations = Uni.createFrom().item(aggregations);
        emailaggregations.onItem().transform(aggregationList -> {
            aggregationList.forEach(emailAggregationItem -> {
                Action action = new Action();
                action.setContext(emailAggregationItem.getPayload().getMap());
                action.setEvents(List.of());
                action.setAccountId(emailAggregationItem.getAccountId());
                action.setApplication(emailAggregationItem.getApplicationName());
                action.setBundle(emailAggregationItem.getBundleName());

                // We don't have a eventtype as this aggregates over multiple event types
                action.setEventType(null);
                action.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

                // We don't have any endpoint (yet) as this aggregates multiple endpoints
                Notification item = new Notification(action, null);

                sendEmail(item, EmailSubscriptionType.DAILY);
            });
            return null;
        });
    }
}
