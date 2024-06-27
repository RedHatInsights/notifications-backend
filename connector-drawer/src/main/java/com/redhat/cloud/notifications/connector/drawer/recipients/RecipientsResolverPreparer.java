package com.redhat.cloud.notifications.connector.drawer.recipients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.RecipientSettings;
import com.redhat.cloud.notifications.connector.drawer.recipients.pojo.RecipientsQuery;
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
public class RecipientsResolverPreparer implements Processor {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void process(final Exchange exchange) throws JsonProcessingException {
        List<RecipientSettings> recipientSettings = exchange.getProperty(ExchangeProperty.RECIPIENT_SETTINGS, List.class);
        Set<String> unsubscribers = exchange.getProperty(ExchangeProperty.UNSUBSCRIBERS, Set.class);
        final String orgId = exchange.getProperty(ORG_ID, String.class);
        JsonObject authorizationCriteria = exchange.getProperty(ExchangeProperty.AUTHORIZATION_CRITERIA, JsonObject.class);

        RecipientsQuery recipientsQuery = new RecipientsQuery();
        recipientsQuery.unsubscribers = unsubscribers;
        recipientsQuery.orgId = orgId;
        recipientsQuery.recipientSettings = Set.copyOf(recipientSettings);
        recipientsQuery.subscribedByDefault = true;
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
