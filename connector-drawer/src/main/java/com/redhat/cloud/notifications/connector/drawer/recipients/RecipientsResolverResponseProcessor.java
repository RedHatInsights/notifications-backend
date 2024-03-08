package com.redhat.cloud.notifications.connector.drawer.recipients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerUser;
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
     * into the {@link DrawerUser} model.
     */
    @Override
    public void process(final Exchange exchange) throws JsonProcessingException {
        final String body = exchange.getMessage().getBody(String.class);
        final List<DrawerUser> recipientsList = Arrays.asList(this.objectMapper.readValue(body, DrawerUser[].class));

        exchange.setProperty(ExchangeProperty.RESOLVED_RECIPIENT_LIST, recipientsList.stream().map(DrawerUser::getUsername).collect(Collectors.toSet()));
    }
}
