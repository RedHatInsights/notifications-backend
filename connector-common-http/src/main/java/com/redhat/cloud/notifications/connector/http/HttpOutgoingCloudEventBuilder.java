package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_CLIENT_ERROR;
import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_SERVER_ERROR;
import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_STATUS_CODE;

@ApplicationScoped
public class HttpOutgoingCloudEventBuilder extends OutgoingCloudEventBuilder {

    public static final String DISABLE_ENDPOINT_CLIENT_ERRORS = "disableEndpointClientErrors";
    public static final String INCREMENT_ENDPOINT_SERVER_ERRORS = "incrementEndpointServerErrors";

    @Override
    public void process(Exchange exchange) throws Exception {
        super.process(exchange);
        boolean clientError = exchange.getProperty(HTTP_CLIENT_ERROR, false, boolean.class);
        boolean serverError = exchange.getProperty(HTTP_SERVER_ERROR, false, boolean.class);

        if (clientError || serverError) {
            Message in = exchange.getIn();
            JsonObject cloudEvent = new JsonObject(in.getBody(String.class));
            JsonObject data = new JsonObject(cloudEvent.getString("data"));
            data.getJsonObject("details").put(HTTP_STATUS_CODE, exchange.getProperty(HTTP_STATUS_CODE));
            if (clientError) {
                data.put(DISABLE_ENDPOINT_CLIENT_ERRORS, exchange.getProperty(HTTP_CLIENT_ERROR));
            } else {
                data.put(INCREMENT_ENDPOINT_SERVER_ERRORS, exchange.getProperty(HTTP_SERVER_ERROR));
            }
            cloudEvent.put("data", data.encode());
            in.setBody(cloudEvent.encode());
        }
    }
}
