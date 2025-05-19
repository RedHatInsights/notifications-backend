package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.http.HttpExceptionProcessor;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class DrawerExceptionProcessor extends HttpExceptionProcessor {

    @Override
    protected void process(Throwable t, Exchange exchange) {
        super.process(t, exchange);
        if (t instanceof HttpOperationFailedException e) {
            exchange.setProperty(ExchangeProperty.ADDITIONAL_ERROR_DETAILS, e.getResponseBody());
        }
    }
}
