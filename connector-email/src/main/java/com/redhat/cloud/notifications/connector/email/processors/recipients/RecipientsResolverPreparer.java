package com.redhat.cloud.notifications.connector.email.processors.recipients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.processors.recipients.pojo.RecipientsQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpMethods;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.EVENT_TYPE_ID;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.SUBSCRIPTION_TYPE;

@ApplicationScoped
public class RecipientsResolverPreparer implements Processor {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void process(final Exchange exchange) throws JsonProcessingException {
        Set<RecipientSettings> recipientSettings = Set.copyOf(exchange.getProperty(ExchangeProperty.RECIPIENT_SETTINGS, List.class));

        RecipientsQuery recipientsQuery = new RecipientsQuery();
        recipientsQuery.orgId = exchange.getProperty(ORG_ID, String.class);
        recipientsQuery.eventTypeId = exchange.getProperty(EVENT_TYPE_ID, UUID.class);
        recipientsQuery.subscriptionType = exchange.getProperty(SUBSCRIPTION_TYPE, String.class);
        recipientsQuery.recipientSettings = recipientSettings;

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
