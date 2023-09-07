package com.redhat.cloud.notifications.connector.email.processors.it;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.processors.it.pojo.response.ITUserResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ITResponseProcessor implements Processor {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Processes the response from the IT service. Grabs the usernames from the
     * response.
     * @param exchange the exchange of the pipeline.
     * @throws JsonProcessingException if the incoming payload cannot be read
     * into the {@link ITUserResponse} model.
     */
    @Override
    public void process(final Exchange exchange) throws JsonProcessingException {
        final String body = exchange.getMessage().getBody(String.class);
        final List<ITUserResponse> itUserResponses = Arrays.asList(this.objectMapper.readValue(body, ITUserResponse[].class));

        // Get the list of usernames we will fill with the extracted usernames
        // from IT's response.
        final Set<String> usernames = exchange.getProperty(ExchangeProperty.USERNAMES, Set.class);

        for (final ITUserResponse itUserResponse : itUserResponses) {
            usernames.add(itUserResponse.authentications.get(0).principal);
        }

        final int count = itUserResponses.size();
        exchange.setProperty(ExchangeProperty.ELEMENTS_COUNT, itUserResponses.size());

        // Update the offset in case we need to fetch the next page.
        final int limit = exchange.getProperty(ExchangeProperty.LIMIT, Integer.class);
        if (count == limit) {
            final int offset = (int) exchange.getProperty(ExchangeProperty.OFFSET);
            exchange.setProperty(ExchangeProperty.OFFSET, offset + limit);
        }
    }
}
