package com.redhat.cloud.notifications.connector.email.processors.bop;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.bop.Email;
import com.redhat.cloud.notifications.connector.email.model.bop.Emails;
import com.redhat.cloud.notifications.connector.email.model.bop.SendEmailsRequest;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpMethods;

import java.util.Set;

import static java.util.stream.Collectors.toSet;

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

        final Set<String> recipients;
        final Set<User> users = exchange.getProperty(ExchangeProperty.FILTERED_USERS, Set.class);
        if (emailConnectorConfig.isSkipBopUsersResolution()) {
            recipients = users.stream().map(User::getEmail).collect(toSet());
            Set<String> emails = exchange.getProperty(ExchangeProperty.EMAIL_RECIPIENTS, Set.class);
            recipients.addAll(emails);
        } else {
            recipients = users.stream().map(User::getUsername).collect(toSet());
        }

        // Prepare the email to be sent.
        final Email email = new Email(
            subject,
            body,
            recipients
        );

        JsonObject bopBody;
        if (emailConnectorConfig.isSkipBopUsersResolution()) {
            final SendEmailsRequest request = new SendEmailsRequest(
                Set.of(email),
                this.emailConnectorConfig.isSkipBopUsersResolution(),
                exchange.getProperty(ExchangeProperty.EMAIL_SENDER, String.class),
                exchange.getProperty(ExchangeProperty.EMAIL_SENDER, String.class)
            );
            bopBody = JsonObject.mapFrom(request);
        } else {
            final Emails emails = new Emails();
            emails.addEmail(email);
            bopBody = JsonObject.mapFrom(emails);
        }

        // Specify the message's payload in JSON.
        exchange.getMessage().setBody(bopBody.encode());

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
