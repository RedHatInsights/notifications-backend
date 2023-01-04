package com.redhat.cloud.notifications.processors.rhose;

import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeAuth;
import com.redhat.cloud.notifications.openbridge.BridgeEventService;
import com.redhat.cloud.notifications.openbridge.RhoseErrorMetricsRecorder;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.templates.models.Environment;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.ProfileManager;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;
import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;
import static com.redhat.cloud.notifications.openbridge.BridgeHelper.ORG_ID_FILTER_NAME;
import static io.quarkus.runtime.LaunchMode.TEST;

@ApplicationScoped
public class RhoseTypeProcessor extends EndpointTypeProcessor {

    public static final String PROCESSED_COUNTER_NAME = "processor.rhose.processed";
    public static final String PROCESSOR_NAME = "processorname";
    public static final String SOURCE = "notifications";
    public static final String SPEC_VERSION = "1.0";
    public static final String TYPE = "myType";

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    MeterRegistry registry;

    @Inject
    BridgeAuth bridgeAuth;

    // TODO There must be a simpler way to deal with this.
    @Inject
    Instance<Bridge> bridgeInstance;

    @Inject
    RhoseErrorMetricsRecorder rhoseErrorMetricsRecorder;

    /**
     * Used to send the environment's URL on RHOSE events.
     */
    @Inject
    Environment environment;

    private Bridge bridge;

    @PostConstruct
    void postConstruct() {
        bridge = bridgeInstance.get();
    }

    /**
     * Resets the bridge instance. Invoking this method is only allowed when
     * the Quarkus launch mode is {@link io.quarkus.runtime.LaunchMode#TEST TEST}.
     */
    public void reset() {
        if (ProfileManager.getLaunchMode() != TEST) {
            throw new IllegalStateException("Illegal bridge reset detected");
        }
        if (bridge != null) {
            bridgeInstance.destroy(bridge);
        }
        postConstruct();
    }

    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        if (!featureFlipper.isObEnabled()) {
            Log.debug("Ob not enabled, doing nothing");
            return;
        }

        DelayedThrower.throwEventually(DELAYED_EXCEPTION_MSG, accumulator -> {
            for (Endpoint endpoint : endpoints) {
                try {
                    process(event, endpoint);
                } catch (Exception e) {
                    accumulator.add(e);
                }
            }
        });
    }

    private void process(Event event, Endpoint endpoint) {
        registry.counter(PROCESSED_COUNTER_NAME, "subType", endpoint.getSubType()).increment();

        String originalEventId = getOriginalEventId(event);
        UUID historyId = UUID.randomUUID();

        CamelProperties properties = endpoint.getProperties(CamelProperties.class);
        Map<String, String> extras = properties.getExtras();

        Log.infof("Sending SmartEvent [orgId=%s, action=%s, processorName=%s, processorId=%s, historyId=%s, originalEventId=%s]",
                endpoint.getOrgId(), endpoint.getSubType(), extras.get(PROCESSOR_NAME), extras.get("processorId"), historyId, originalEventId);

        long startTime = System.currentTimeMillis();
        JsonObject outgoingEvent = buildOutgoingEvent(event, endpoint, extras, historyId, originalEventId);

        NotificationHistory history = getHistoryStub(endpoint, event, 0L, historyId);
        try {
            sendEvent(outgoingEvent);
            history.setStatus(NotificationStatus.SENT);
        } catch (Exception e) {
            history.setStatus(NotificationStatus.FAILED_INTERNAL);
            history.setDetails(Map.of("failure", e.getMessage()));
            Log.infof(e, "Sending SmartEvent failed [historyId=%s, originalEventId=%s]", historyId, originalEventId);
        }
        long invocationTime = System.currentTimeMillis() - startTime;
        history.setInvocationTime(invocationTime);
        persistNotificationHistory(history);
    }

    private static String getOriginalEventId(Event event) {
        String originalEventId = "-not provided-";
        if (event.getId() != null) {
            originalEventId = event.getId().toString();
        }
        return originalEventId;
    }

    private JsonObject buildOutgoingEvent(Event event, Endpoint endpoint, Map<String, String> extras, UUID historyId, String originalEventId) {
        JsonObject data = baseTransformer.toJsonObject(event.getAction());
        data.put("environment_url", environment.url());

        // TODO add dataschema
        JsonObject outgoingEvent = new JsonObject();
        outgoingEvent.put("id", historyId);
        outgoingEvent.put("source", SOURCE); // Source of original event?
        outgoingEvent.put("specversion", SPEC_VERSION);
        outgoingEvent.put("type", TYPE); // Type of original event?
        outgoingEvent.put(ORG_ID_FILTER_NAME, endpoint.getOrgId());
        outgoingEvent.put(PROCESSOR_NAME, extras.get(PROCESSOR_NAME));
        outgoingEvent.put("originaleventid", originalEventId);
        outgoingEvent.put("data", data);

        return outgoingEvent;
    }

    private void sendEvent(JsonObject outgoingEvent) {
        // TODO Can we cache and reuse this instance instead of building a new one on each call?
        BridgeEventService eventService = RestClientBuilder.newBuilder()
                .baseUri(URI.create(bridge.getEndpoint()))
                .build(BridgeEventService.class);

        try {
            eventService.sendEvent(outgoingEvent, bridgeAuth.getToken());
        } catch (WebApplicationException e) {
            String path = "POST " + bridge.getEndpoint();
            rhoseErrorMetricsRecorder.record(path, e);
            throw e;
        }
    }
}
