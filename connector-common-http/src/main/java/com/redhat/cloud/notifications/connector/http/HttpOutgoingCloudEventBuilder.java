package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.REDELIVERY_ATTEMPTS;
import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_ERROR_TYPE;
import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_STATUS_CODE;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_5XX;

@ApplicationScoped
public class HttpOutgoingCloudEventBuilder extends OutgoingCloudEventBuilder {

    @Override
    public void process(Exchange exchange) throws Exception {
        super.process(exchange);

        HttpErrorType httpErrorType = exchange.getProperty(HTTP_ERROR_TYPE, HttpErrorType.class);
        if (httpErrorType != null) {
            Message in = exchange.getIn();
            JsonObject cloudEvent = new JsonObject(in.getBody(String.class));
            JsonObject data = new JsonObject(cloudEvent.getString("data"));
            JsonObject error = new JsonObject();
            error.put("error_type", httpErrorType);
            error.put("delivery_attempts", 1 + exchange.getProperty(REDELIVERY_ATTEMPTS, 0, int.class));
            if (httpErrorType == HTTP_4XX || httpErrorType == HTTP_5XX) {
                error.put("http_status_code", exchange.getProperty(HTTP_STATUS_CODE));
            }
            data.put("error", error);
            cloudEvent.put("data", data.encode());
            in.setBody(cloudEvent.encode());
        }
    }
}
