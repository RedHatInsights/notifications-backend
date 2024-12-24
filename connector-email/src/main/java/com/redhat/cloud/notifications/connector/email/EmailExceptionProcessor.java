package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.http.HttpExceptionProcessor;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;

import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.ADDITIONAL_ERROR_DETAILS;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class EmailExceptionProcessor extends HttpExceptionProcessor {

    @Override
    protected void process(Throwable t, Exchange exchange) {
        super.process(t, exchange);
        if (t instanceof HttpOperationFailedException e) {
            exchange.setProperty(ADDITIONAL_ERROR_DETAILS, e.getResponseBody());
        }
    }
}
