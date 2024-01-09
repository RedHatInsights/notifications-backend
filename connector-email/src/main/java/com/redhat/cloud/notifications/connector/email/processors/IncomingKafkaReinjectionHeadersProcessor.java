package com.redhat.cloud.notifications.connector.email.processors;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsResolverResponseProcessor;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.FILTERED_USERS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENTS_WITH_EMAIL_ERROR;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class IncomingKafkaReinjectionHeadersProcessor extends com.redhat.cloud.notifications.connector.IncomingKafkaReinjectionHeadersProcessor {

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Override
    public void process(final Exchange exchange) {
        super.process(exchange);
        Optional<String> recipientsWithError = Optional.ofNullable(exchange.getMessage().getHeader(RECIPIENTS_WITH_EMAIL_ERROR, String.class));
        if (recipientsWithError.isPresent()) {
            Set<String> recipients = new HashSet<>(Arrays.asList(recipientsWithError.get().split(",")));
            exchange.setProperty(FILTERED_USERS, RecipientsResolverResponseProcessor.partition(recipients, emailConnectorConfig.getMaxRecipientsPerEmail() - 1));
        }
        Optional<String> totalRecipients = Optional.ofNullable(exchange.getMessage().getHeader(TOTAL_RECIPIENTS_KEY, String.class));
        if (totalRecipients.isPresent()) {
            exchange.setProperty(TOTAL_RECIPIENTS_KEY, Integer.parseInt(totalRecipients.get()));
        }

        Log.info("FiltredUsers : " + exchange.getProperty(FILTERED_USERS, Set.class));
        Log.info("total recipients : " + exchange.getProperty(TOTAL_RECIPIENTS_KEY, Integer.class));
    }
}
