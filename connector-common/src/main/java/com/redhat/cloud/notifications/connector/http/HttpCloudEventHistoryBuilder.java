package com.redhat.cloud.notifications.connector.http;

import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

import static com.redhat.cloud.notifications.connector.http.Constants.DISABLE_ENDPOINT_CLIENT_ERRORS;
import static com.redhat.cloud.notifications.connector.http.Constants.HTTP_STATUS_CODE;
import static com.redhat.cloud.notifications.connector.http.Constants.INCREMENT_ENDPOINT_SERVER_ERRORS;

public class HttpCloudEventHistoryBuilder {

    public static void process(Exchange exchange) throws Exception {
        boolean clientError = exchange.getProperty(DISABLE_ENDPOINT_CLIENT_ERRORS, false, boolean.class);
        boolean serverError = exchange.getProperty(INCREMENT_ENDPOINT_SERVER_ERRORS, false, boolean.class);

        if (clientError || serverError) {
            Message in = exchange.getIn();
            JsonObject cloudEvent = new JsonObject(in.getBody(String.class));
            JsonObject data = new JsonObject(cloudEvent.getString("data"));
            data.getJsonObject("details").put(HTTP_STATUS_CODE, exchange.getProperty(HTTP_STATUS_CODE));
            if (clientError) {
                data.put(DISABLE_ENDPOINT_CLIENT_ERRORS, exchange.getProperty(DISABLE_ENDPOINT_CLIENT_ERRORS));
            } else {
                data.put(INCREMENT_ENDPOINT_SERVER_ERRORS, exchange.getProperty(INCREMENT_ENDPOINT_SERVER_ERRORS));
            }
            cloudEvent.put("data", data.encode());
            in.setBody(cloudEvent.encode());
        }
    }
}
