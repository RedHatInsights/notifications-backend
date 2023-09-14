package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;

import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.ACCOUNT_ID;

@ApplicationScoped
public class ServiceNowExceptionProcessor extends ExceptionProcessor {

    private static final String DEFAULT_LOG_MSG = "Message sending failed on %s: [orgId=%s, accountId=%s, historyId=%s, targetUrl=%s]";
    private static final String HTTP_LOG_MSG = "Message sending failed on %s: [orgId=%s, accountId=%s, historyId=%s, targetUrl=%s] " +
            "with status code [%d] and body [%s]";

    @Override
    protected void process(Throwable t, Exchange exchange) {
        if (t instanceof HttpOperationFailedException e) {
            Log.errorf(
                    HTTP_LOG_MSG,
                    getRouteId(exchange),
                    getOrgId(exchange),
                    exchange.getProperty(ACCOUNT_ID, String.class),
                    getExchangeId(exchange),
                    getTargetUrl(exchange),
                    e.getStatusCode(),
                    e.getResponseBody()
            );
        } else {
            Log.errorf(
                    t,
                    DEFAULT_LOG_MSG,
                    getRouteId(exchange),
                    getOrgId(exchange),
                    exchange.getProperty(ACCOUNT_ID, String.class),
                    getExchangeId(exchange),
                    getTargetUrl(exchange)
            );
        }
    }
}
