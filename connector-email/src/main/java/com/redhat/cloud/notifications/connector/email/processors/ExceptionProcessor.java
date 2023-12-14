package com.redhat.cloud.notifications.connector.email.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.bop.SendEmailsRequest;
import com.redhat.cloud.notifications.connector.http.HttpExceptionProcessor;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.jboss.logging.Logger;
import java.util.HashSet;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENTS_WITH_EMAIL_ERROR;
import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.*;

@Alternative
@Priority(0) // The value doesn't matter.
@ApplicationScoped
public class ExceptionProcessor extends HttpExceptionProcessor {

    private static final String BOP_ERROR_LOG_MSG = "Message sending failed [routeId=%s, orgId=%s, historyId=%s, targetUrl=%s, statusCode=%d, responseBody=%s, recipients=%s]";

    @Inject
    EmailConnectorConfig connectorConfig;

    @Inject
    ObjectMapper objectMapper;

    @Override
    protected void process(Throwable t, Exchange exchange) {
        if (t instanceof HttpOperationFailedException e &&
            exchange.getProperty(Exchange.TO_ENDPOINT).equals(connectorConfig.getBopURL())) {
            exchange.setProperty(HTTP_STATUS_CODE, e.getStatusCode());

            try {
                SendEmailsRequest request = objectMapper.readValue(exchange.getIn().getBody(String.class), SendEmailsRequest.class);
                if (request.emails.stream().findFirst().isPresent()) {
                    Set<String> recipientsWithError = exchange.getProperty(RECIPIENTS_WITH_EMAIL_ERROR, Set.class);
                    if (recipientsWithError == null) {
                        recipientsWithError = new HashSet<>();
                    }
                    recipientsWithError.addAll(request.emails.stream().findFirst().get().getBccList());
                    exchange.setProperty(RECIPIENTS_WITH_EMAIL_ERROR, recipientsWithError);
                    logBopError(connectorConfig.getServerErrorLogLevel(), e, exchange, request.emails.stream().findFirst().get().getBccList());
                }
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            super.process(t, exchange);
        }
    }

    private void logBopError(Logger.Level level, HttpOperationFailedException e, Exchange exchange, Set<String> errorRecipients) {
        Log.logf(
                level,
                BOP_ERROR_LOG_MSG,
                getRouteId(exchange),
                getOrgId(exchange),
                getExchangeId(exchange),
                getTargetUrl(exchange),
                e.getStatusCode(),
                e.getResponseBody(),
                errorRecipients
        );
    }
}
