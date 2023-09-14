package com.redhat.cloud.notifications.connector.email.processors.dispatcher;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;

import java.util.HashSet;
import java.util.List;

@ApplicationScoped
public class DispatcherProcessor implements Processor {
    @Inject
    ProducerTemplate producerTemplate;

    @Override
    public void process(final Exchange exchange) {
        // Initialize the set that will hold all the usernames that are fetched
        // from the user providers.
        exchange.setProperty(ExchangeProperty.USERNAMES, new HashSet<>());

        final List<RecipientSettings> recipientSettings = exchange.getProperty(ExchangeProperty.RECIPIENT_SETTINGS, List.class);
        for (final RecipientSettings settings : recipientSettings) {
            // Store the settings that will need to be used in further routes.
            exchange.setProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, settings);

            if (null == settings.getGroupUUID()) {
                this.producerTemplate.send(String.format("direct:%s", Routes.FETCH_USERS), exchange);
            } else {
                exchange.setProperty(ExchangeProperty.GROUP_UUID, settings.getGroupUUID());

                this.producerTemplate.send(String.format("direct:%s", Routes.FETCH_GROUP), exchange);
            }
        }
    }
}
