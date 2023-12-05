package com.redhat.cloud.notifications.connector.email.processors.recipients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class RecipientsResolverResponseProcessor implements Processor {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EmailConnectorConfig emailConnectorConfig;

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
        final Set<String> recipientsList = new HashSet<>(Arrays.asList(this.objectMapper.readValue(body, User[].class)))
            .stream().map(User::getEmail).collect(toSet());

        Set<String> emails = exchange.getProperty(ExchangeProperty.EMAIL_RECIPIENTS, Set.class);
        recipientsList.addAll(emails);

        // We have to remove one from the limit, because a default recipient (like noreply@redhat.com) will be automatically added
        exchange.setProperty(ExchangeProperty.FILTERED_USERS, partition(recipientsList, emailConnectorConfig.getMaxRecipientsPerEmail() -1));
    }

    private static Set<List<String>> partition(Set<String> collection, int n) {
        AtomicInteger counter = new AtomicInteger();
        return collection.stream()
            .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / n))
            .values().stream().collect(Collectors.toSet());
    }
}
