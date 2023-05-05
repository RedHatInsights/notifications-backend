package com.redhat.cloud.notifications.processors.common.camel;

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
public class HttpOperationFailedExceptionProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Throwable t = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        if (!(t instanceof HttpOperationFailedException)) {
            return;
        }

        HttpOperationFailedException httpException = (HttpOperationFailedException) t;

        String responseBody = httpException.getResponseBody();
        Map<String, String> responseHeaders = httpException.getResponseHeaders();

        StringBuilder headerBuilder = new StringBuilder();
        for (String key : responseHeaders.keySet()) {
            headerBuilder.append(key).append(":").append(responseHeaders.get(key)).append(",");
        }

        Log.infof("Received error response from notification [orgId=%s, historyId=%s, webhookUrl=%s] with return code: [%s], headers: [%s], body: [%s]",
            exchange.getProperty("orgId"),
            exchange.getProperty("historyId"),
            exchange.getProperty("webhookUrl"),
            httpException.getHttpResponseCode(),
            headerBuilder.toString(),
            responseBody);
        throw httpException;
    }
}
