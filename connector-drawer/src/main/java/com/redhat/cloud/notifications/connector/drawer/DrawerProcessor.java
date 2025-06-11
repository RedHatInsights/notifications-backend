package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntryPayload;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerUser;
import com.redhat.cloud.notifications.connector.drawer.model.RecipientSettings;
import com.redhat.cloud.notifications.connector.drawer.recipients.recipientsresolver.ExternalRecipientsResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import java.util.List;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.drawer.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class DrawerProcessor implements Processor {

    static final String RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC = "email.recipients_resolver.response.time";

    public static final String DRAWER_CHANNEL = "drawer";

    @Inject
    ExternalRecipientsResolver externalRecipientsResolver;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    @Channel(DRAWER_CHANNEL)
    Emitter<JsonObject> emitter;


    @Override
    public void process(final Exchange exchange) {
        // fetch recipients
        Set<String> recipientsList = fetchRecipients(exchange);

        if (recipientsList.isEmpty()) {
            Log.infof("Skipped Email notification because the recipients list was empty [orgId=$%s, historyId=%s]", exchange.getProperty(ORG_ID, String.class), exchange.getProperty(ID, String.class));
        } else {
            // send to kafka chrome service
            final DrawerEntryPayload entryPayloadModel = exchange.getProperty(ExchangeProperty.DRAWER_ENTRY_PAYLOAD, DrawerEntryPayload.class);
            JsonObject drawerPayload = DrawerPayloadBuilder.buildDrawerPayload(entryPayloadModel, recipientsList);
            emitter.send(drawerPayload);
        }
    }

    private Set<String> fetchRecipients(Exchange exchange) {
        List<RecipientSettings> recipientSettings = exchange.getProperty(ExchangeProperty.RECIPIENT_SETTINGS, List.class);
        Set<String> unsubscribers = exchange.getProperty(ExchangeProperty.UNSUBSCRIBERS, Set.class);
        final String orgId = exchange.getProperty(ORG_ID, String.class);
        JsonObject authorizationCriterion = exchange.getProperty(ExchangeProperty.AUTHORIZATION_CRITERIA, JsonObject.class);

        boolean subscribedByDefault = true;
        final Timer.Sample recipientsResolverResponseTimeMetric = Timer.start(meterRegistry);
        Set<String> recipientsList = externalRecipientsResolver.recipientUsers(
                orgId,
                Set.copyOf(recipientSettings),
                unsubscribers,
                subscribedByDefault,
                authorizationCriterion)
            .stream().map(DrawerUser::getUsername).filter(username -> username != null && !username.isBlank()).collect(toSet());
        recipientsResolverResponseTimeMetric.stop(meterRegistry.timer(RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC));

        exchange.setProperty(TOTAL_RECIPIENTS_KEY, recipientsList.size());
        return recipientsList;
    }
}
