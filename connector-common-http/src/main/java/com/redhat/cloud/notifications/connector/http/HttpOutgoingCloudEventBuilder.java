package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

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
            data.put(HTTP_ERROR_TYPE, httpErrorType);
            if (httpErrorType == HTTP_4XX || httpErrorType == HTTP_5XX) {
                data.getJsonObject("details").put(HTTP_STATUS_CODE, exchange.getProperty(HTTP_STATUS_CODE));
            }
            cloudEvent.put("data", data.encode());
            in.setBody(cloudEvent.encode());
        }
    }
}
