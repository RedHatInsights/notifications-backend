package com.redhat.cloud.notifications.connector.email.processors.it;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.processors.it.pojo.request.ITUserRequest;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpMethods;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ITUserRequestPreparer implements Processor {
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Prepares a {@link ITUserRequest} object, transforms it to JSON, and sets
     * it to the exchange's body, ready to be sent.
     * @param exchange the exchange from the pipeline.
     * @throws JsonProcessingException if the {@link ITUserRequest} object
     * cannot be serialized.
     */
    @Override
    public void process(final Exchange exchange) throws JsonProcessingException {
        final String orgId = exchange.getProperty(com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID, String.class);
        final RecipientSettings recipientSettings = exchange.getProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, RecipientSettings.class);

        // Grab the current offset that will be used to fetch different pages
        // from IT.
        final int offset = exchange.getProperty(ExchangeProperty.OFFSET, Integer.class);

        // Prepare the payload.
        final ITUserRequest itUserRequest = new ITUserRequest(orgId, recipientSettings.isAdminsOnly(), offset, this.emailConnectorConfig.getItElementsPerPage());

        // Serialize the payload.
        exchange.getMessage().setBody(this.objectMapper.writeValueAsString(itUserRequest));

        // Set the "Accept" header for the incoming payload.
        exchange.getMessage().setHeader("Accept", "application/json");

        // Set the "Content Type" header for our payload.
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");

        // Set the path and the method of the request.
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/v2/findUsers");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, HttpMethods.POST);
    }
}
