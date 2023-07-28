package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;

public class ImageBuilderAggregator extends AbstractEmailPayloadAggregator {

    private static final String EVENT_TYPE = "event_type";
    private static final String LAUNCH_SUCCESS = "launch-success";
    private static final String LAUNCH_FAILURE = "launch-failed";
    private static final String AWS = "aws";
    private static final String GCP = "gcp";
    private static final String AZURE = "azure";

    private static final List<String> EVENT_TYPES = Arrays.asList(LAUNCH_SUCCESS, LAUNCH_FAILURE);

    private static final String IMAGE_LAUNCH_KEY = "images";
    private static final String EVENTS_KEY = "events";
    private static final String CONTEXT_KEY = "context";
    private static final String PAYLOAD_KEY = "payload";
    private static final String PROVIDER = "provider";
    private static final String INSTANCES_KEY = "instances";
    private static final String FAILURE_KEY = "failure";
    private static final String LAUNCH_SUCCESS_KEY = "launch_success";
    private static final String ERRORS = "errors";
    private static final String PROVIDER_INIT_JSON = "{\"instances\": 0, \"launch_success\": 0, \"failure\": 0}";

    public ImageBuilderAggregator() {
        JsonObject imageLaunch = new JsonObject();
        imageLaunch.put(AWS, new JsonObject(PROVIDER_INIT_JSON));
        imageLaunch.put(GCP, new JsonObject(PROVIDER_INIT_JSON));
        imageLaunch.put(AZURE, new JsonObject(PROVIDER_INIT_JSON));
        imageLaunch.put(ERRORS, new JsonArray());
        imageLaunch.put(LAUNCH_SUCCESS_KEY, 0);

        context.put(IMAGE_LAUNCH_KEY, imageLaunch);
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        JsonObject imageLaunch = context.getJsonObject(IMAGE_LAUNCH_KEY);
        JsonObject notificationJson = notification.getPayload();
        String eventType = notificationJson.getString(EVENT_TYPE);

        if (!EVENT_TYPES.contains(eventType)) {
            return;
        }

        JsonObject context = notificationJson.getJsonObject(CONTEXT_KEY);
        String provider = context.getString(PROVIDER);
        Integer numberOfInstances = notificationJson.getJsonArray(EVENTS_KEY).size();
        Integer prevNumberOfInstances;
        Integer prevNumberOfFailures;

        switch (eventType) {
            case LAUNCH_SUCCESS:
                prevNumberOfInstances = imageLaunch.getJsonObject(provider).getInteger(INSTANCES_KEY);
                imageLaunch.getJsonObject(provider).put(INSTANCES_KEY, prevNumberOfInstances + numberOfInstances);
                imageLaunch.put(LAUNCH_SUCCESS_KEY, imageLaunch.getInteger(LAUNCH_SUCCESS_KEY) + 1);
                imageLaunch.getJsonObject(provider).put(LAUNCH_SUCCESS_KEY, imageLaunch.getJsonObject(provider).getInteger(LAUNCH_SUCCESS_KEY) + 1);
                break;
            case LAUNCH_FAILURE:
                prevNumberOfFailures = imageLaunch.getJsonObject(provider).getInteger(FAILURE_KEY);
                imageLaunch.getJsonObject(provider).put(FAILURE_KEY, prevNumberOfFailures + 1);
                notificationJson.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
                    JsonObject event = (JsonObject) eventObject;
                    JsonObject payload = event.getJsonObject(PAYLOAD_KEY);

                    JsonArray collection = imageLaunch.getJsonArray(ERRORS);
                    collection.add(payload.getString(ERRORS));
                });
                break;
            default:
                break;
        }
    }
}
