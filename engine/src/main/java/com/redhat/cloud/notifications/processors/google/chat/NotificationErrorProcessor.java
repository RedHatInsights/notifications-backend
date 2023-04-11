package com.redhat.cloud.notifications.processors.google.chat;

import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.http.base.HttpOperationFailedException;
import javax.enterprise.context.ApplicationScoped;
import java.util.Map;

/*
 * This processor logs an Http error response
 */
@ApplicationScoped
public class NotificationErrorProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Throwable t = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        if (!(t instanceof HttpOperationFailedException)) {
            return;
        }

        HttpOperationFailedException httpException = (HttpOperationFailedException) t;

        String responseBody = httpException.getResponseBody();
        Map<String, String> responseHeaders = httpException.getResponseHeaders();

        StringBuilder builder = new StringBuilder();
        for (String key : responseHeaders.keySet()) {
            builder.append(key).append(":").append(responseHeaders.get(key)).append(",");
        }

        Log.infof("Received error response from endpoint: %s, body: %s, headers: %s",
            new String[]{(exchange.getFromEndpoint() != null ? exchange.getFromEndpoint().getEndpointUri() :
                    "unknown"), responseBody, builder.toString()});
        throw httpException;
    }
}
