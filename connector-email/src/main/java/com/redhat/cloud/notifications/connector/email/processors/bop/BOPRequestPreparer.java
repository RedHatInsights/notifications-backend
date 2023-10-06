package com.redhat.cloud.notifications.connector.email.processors.bop;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.bop.Email;
import com.redhat.cloud.notifications.connector.email.model.bop.Emails;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpMethods;

import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class BOPRequestPreparer implements Processor {
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    /**
     * Prepares the payload that BOP expects.
     * @param exchange the exchange of the pipeline.
     */
    @Override
    public void process(final Exchange exchange) {
        final String subject = exchange.getProperty(ExchangeProperty.RENDERED_SUBJECT, String.class);
        final String body = exchange.getProperty(ExchangeProperty.RENDERED_BODY, String.class);
        final Set<String> recipients = new HashSet<>();

        // We still need to support sending individual emails per user for a
        // while. However, that will go away soon, so we can consider the
        // following code block very much deprecated.
        final Boolean singleEmailPerUser = exchange.getProperty(ExchangeProperty.SINGLE_EMAIL_PER_USER, Boolean.class);
        if (singleEmailPerUser != null && singleEmailPerUser) {
            recipients.add(exchange.getMessage().getBody(String.class));
        } else {
            final Set<String> usernames = exchange.getProperty(ExchangeProperty.FILTERED_USERNAMES, Set.class);

            recipients.addAll(usernames);
        }

        final Email email = new Email(
            subject,
            body,
            recipients
        );

        final Emails emails = new Emails();
        emails.addEmail(email);

        // Specify the message's payload in JSON.
        exchange.getMessage().setBody(JsonObject.mapFrom(emails).encode());

        // Specify the request's method.
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, HttpMethods.POST);

        // Specify the request's path.
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/v1/sendEmails");

        // Specify the payload's content type.
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");

        // Specify the authentication details required by BOP.
        exchange.getMessage().setHeader(Constants.BOP_API_TOKEN_HEADER, this.emailConnectorConfig.getBopApiToken());
        exchange.getMessage().setHeader(Constants.BOP_CLIENT_ID_HEADER, this.emailConnectorConfig.getBopClientId());
        exchange.getMessage().setHeader(Constants.BOP_ENV_HEADER, this.emailConnectorConfig.getBopEnv());
    }
}
