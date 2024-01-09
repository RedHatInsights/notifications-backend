package com.redhat.cloud.notifications.connector.email.processors.bop;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.bop.Email;
import com.redhat.cloud.notifications.connector.email.model.bop.SendEmailsRequest;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpMethods;

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
        Set<String> recipients = exchange.getMessage().getBody(Set.class);
        exchange.setProperty(ExchangeProperty.RECIPIENTS_SIZE, recipients.size());

        // Prepare the email to be sent.
        final Email email = new Email(
            subject,
            body,
            recipients
        );

        JsonObject bopBody;

        final SendEmailsRequest request = new SendEmailsRequest(
            Set.of(email),
            exchange.getProperty(ExchangeProperty.EMAIL_SENDER, String.class),
            exchange.getProperty(ExchangeProperty.EMAIL_SENDER, String.class)
        );
        bopBody = JsonObject.mapFrom(request);

        // Specify the message's payload in JSON.
        exchange.getMessage().setBody(bopBody.encode());

        // Specify the request's method.
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, HttpMethods.POST);

        // Specify the request's path.
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/v1/sendEmails");

        // Specify the payload's content type.
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json; charset=utf-8");

        // Specify the authentication details required by BOP.
        exchange.getMessage().setHeader(Constants.BOP_API_TOKEN_HEADER, this.emailConnectorConfig.getBopApiToken());
        exchange.getMessage().setHeader(Constants.BOP_CLIENT_ID_HEADER, this.emailConnectorConfig.getBopClientId());
        exchange.getMessage().setHeader(Constants.BOP_ENV_HEADER, this.emailConnectorConfig.getBopEnv());
    }
}
