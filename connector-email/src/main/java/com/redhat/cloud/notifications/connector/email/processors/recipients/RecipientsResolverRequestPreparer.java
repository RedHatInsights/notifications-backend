package com.redhat.cloud.notifications.connector.email.processors.recipients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpMethods;
import java.util.List;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;

@ApplicationScoped
public class RecipientsResolverRequestPreparer implements Processor {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void process(final Exchange exchange) throws JsonProcessingException {
        List<RecipientSettings> recipientSettings = exchange.getProperty(ExchangeProperty.RECIPIENT_SETTINGS, List.class);
        Set<String> subscribers = exchange.getProperty(ExchangeProperty.SUBSCRIBERS, Set.class);
        Set<String> unsubscribers = exchange.getProperty(ExchangeProperty.UNSUBSCRIBERS, Set.class);
        JsonObject authorizationCriteria = exchange.getProperty(ExchangeProperty.AUTHORIZATION_CRITERIA, JsonObject.class);

        boolean subscribedByDefault = exchange.getProperty(ExchangeProperty.SUBSCRIBED_BY_DEFAULT, boolean.class);
        final String orgId = exchange.getProperty(ORG_ID, String.class);

        RecipientsQuery recipientsQuery = new RecipientsQuery();
        recipientsQuery.subscribers = subscribers;
        recipientsQuery.unsubscribers = unsubscribers;
        recipientsQuery.orgId = orgId;
        recipientsQuery.recipientSettings = Set.copyOf(recipientSettings);
        recipientsQuery.subscribedByDefault = subscribedByDefault;
        recipientsQuery.authorizationCriteria = authorizationCriteria;

        // Serialize the payload.
        exchange.getMessage().setBody(objectMapper.writeValueAsString(recipientsQuery));

        // Set the "Accept" header for the incoming payload.
        exchange.getMessage().setHeader("Accept", "application/json");

        // Set the "Content Type" header for our payload.
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");

        // Set the path and the method of the request.
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, HttpMethods.PUT);
    }
}
