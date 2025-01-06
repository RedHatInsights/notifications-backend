package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import com.redhat.cloud.notifications.connector.email.processors.bop.BOPManager;
import com.redhat.cloud.notifications.connector.email.processors.recipientsresolver.ExternalRecipientsResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class EmailManagementProcessor implements Processor {

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    ExternalRecipientsResolver externalRecipientsResolver;

    @Inject
    BOPManager bopManager;

    @Inject
    MeterRegistry meterRegistry;

    static final String BOP_RESPONSE_TIME_METRIC = "email.bop.response.time";
    static final String RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC = "email.recipients_resolver.response.time";

    @Override
    public void process(final Exchange exchange) {
        // fetch recipients
        Set<String> recipientsList = fetchRecipients(exchange);

        if (recipientsList.isEmpty()) {
            Log.infof("Skipped Email notification because the recipients list was empty [orgId=$%s, historyId=%s]", exchange.getProperty(ORG_ID, String.class), exchange.getProperty(ID, String.class));
        } else {
            // send to bop
            sendToBop(exchange, recipientsList);
        }
    }

    private void sendToBop(Exchange exchange, Set<String> recipientsList) {
        // split recipient list and send it ot BOP
        List<List<String>> packedRecipients = partition(recipientsList, emailConnectorConfig.getMaxRecipientsPerEmail() - 1);
        final String subject = exchange.getProperty(ExchangeProperty.RENDERED_SUBJECT, String.class);
        final String body = exchange.getProperty(ExchangeProperty.RENDERED_BODY, String.class);
        final String sender = exchange.getProperty(ExchangeProperty.EMAIL_SENDER, String.class);

        for (int i = 0; i < packedRecipients.size(); i++) {
            final Timer.Sample bopResponseTimeMetric = Timer.start(meterRegistry);
            bopManager.sendToBop(packedRecipients.get(i), subject, body, sender);
            bopResponseTimeMetric.stop(meterRegistry.timer(BOP_RESPONSE_TIME_METRIC));
            Log.infof("Sent Email notification %d/%d [orgId=%s, historyId=%s]", i + 1, packedRecipients.size(), exchange.getProperty(ORG_ID, String.class), exchange.getProperty(ID, String.class));
        }
    }

    private static List<List<String>> partition(Set<String> collection, int n) {
        AtomicInteger counter = new AtomicInteger();
        return collection.stream()
            .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / n))
            .values().stream().toList();
    }

    private Set<String> fetchRecipients(Exchange exchange) {
        List<RecipientSettings> recipientSettings = exchange.getProperty(ExchangeProperty.RECIPIENT_SETTINGS, List.class);
        Set<String> subscribers = exchange.getProperty(ExchangeProperty.SUBSCRIBERS, Set.class);
        Set<String> unsubscribers = exchange.getProperty(ExchangeProperty.UNSUBSCRIBERS, Set.class);
        JsonObject recipientsAuthorizationCriterion = exchange.getProperty(ExchangeProperty.RECIPIENTS_AUTHORIZATION_CRITERION, JsonObject.class);

        boolean subscribedByDefault = exchange.getProperty(ExchangeProperty.SUBSCRIBED_BY_DEFAULT, boolean.class);
        final String orgId = exchange.getProperty(ORG_ID, String.class);

        final Timer.Sample recipientsResolverResponseTimeMetric = Timer.start(meterRegistry);
        Set<String> recipientsList = externalRecipientsResolver.recipientUsers(
                orgId,
                Set.copyOf(recipientSettings),
                subscribers,
                unsubscribers,
                subscribedByDefault,
                recipientsAuthorizationCriterion)
            .stream().map(User::getEmail).filter(email -> email != null && !email.isBlank()).collect(toSet());
        recipientsResolverResponseTimeMetric.stop(meterRegistry.timer(RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC));

        Set<String> emails = exchange.getProperty(ExchangeProperty.EMAIL_RECIPIENTS, Set.of(), Set.class);
        if (emailConnectorConfig.isEmailsInternalOnlyEnabled()) {
            Set<String> forbiddenEmail = emails.stream().filter(email -> !email.trim().toLowerCase().endsWith("@redhat.com")).collect(Collectors.toSet());
            if (!forbiddenEmail.isEmpty()) {
                Log.warnf(" %s emails are forbidden for message historyId: %s ", forbiddenEmail, exchange.getProperty(com.redhat.cloud.notifications.connector.ExchangeProperty.ID, String.class));
            }
            emails.removeAll(forbiddenEmail);
        }
        recipientsList.addAll(emails);
        exchange.setProperty(TOTAL_RECIPIENTS_KEY, recipientsList.size());
        return recipientsList;
    }
}
