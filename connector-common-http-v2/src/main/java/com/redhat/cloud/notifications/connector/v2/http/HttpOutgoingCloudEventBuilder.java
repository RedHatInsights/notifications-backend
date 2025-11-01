package com.redhat.cloud.notifications.connector.v2.http;


import com.redhat.cloud.notifications.connector.v2.OutgoingCloudEventBuilder;
import com.redhat.cloud.notifications.connector.v2.http.pojo.HandledHttpExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.http.pojo.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.pojo.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.pojo.HandledMessageDetails;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_3XX;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_5XX;

@ApplicationScoped
public class HttpOutgoingCloudEventBuilder extends OutgoingCloudEventBuilder {

    @Override
    public JsonObject buildFailure(HandledExceptionDetails processedExceptionDetails) {
        JsonObject data = new JsonObject();
        if (processedExceptionDetails instanceof HandledHttpExceptionDetails processedExceptionDetailsHttp) {
            if (null != processedExceptionDetailsHttp.targetUrl) {
                data.put("details", new JsonObject().put("target", processedExceptionDetailsHttp.targetUrl));
            }

            if (processedExceptionDetailsHttp.httpErrorType != null) {
                JsonObject error = new JsonObject();
                error.put("error_type", processedExceptionDetailsHttp.httpErrorType);
                if (List.of(HTTP_3XX, HTTP_4XX, HTTP_5XX).contains(processedExceptionDetailsHttp.httpErrorType)) {
                    error.put("http_status_code", processedExceptionDetailsHttp.httpStatusCode);
                }
                data.put("error", error);
            }
        }
        return data;
    }

    @Override
    public JsonObject buildSuccess(HandledMessageDetails processedMessageDetails) {
        JsonObject details = new JsonObject();
        if (processedMessageDetails instanceof HandledHttpMessageDetails processedHttpMessageDetails) {
            if (null != processedHttpMessageDetails.targetUrl) {
                details.put("details", new JsonObject().put("target", processedHttpMessageDetails.targetUrl));
            }
        }
        return details;
    }
}
