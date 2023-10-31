package com.redhat.cloud.notifications.connector.email.processors.recipients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class RecipientsResolverResponseProcessor implements Processor {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Processes the response from the recipients-resolver service. Grabs the users from the
     * response.
     * @param exchange the exchange of the pipeline.
     * @throws JsonProcessingException if the incoming payload cannot be read
     * into the {@link User} model.
     */
    @Override
    public void process(final Exchange exchange) throws JsonProcessingException {
        final String body = exchange.getMessage().getBody(String.class);
        final List<User> recipientsList = Arrays.asList(this.objectMapper.readValue(body, User[].class));

        exchange.setProperty(ExchangeProperty.FILTERED_USERS, recipientsList.stream().collect(Collectors.toSet()));
    }
}
