package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.Base64Utils;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.converters.MapConverter;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeAuth;
import com.redhat.cloud.notifications.openbridge.BridgeEventService;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.context.Context;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.JsonObject;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;
import static java.nio.charset.StandardCharsets.UTF_8;

// TODO NOTIF-603 Migrate to orgId (not started in this class)
@ApplicationScoped
public class CamelTypeProcessor implements EndpointTypeProcessor {

    public static final String TOCAMEL_CHANNEL = "toCamel";
    public static final String PROCESSED_COUNTER_NAME = "processor.camel.processed";
    public static final String TOKEN_HEADER = "X-Insight-Token";
    public static final String NOTIF_METADATA_KEY = "notif-metadata";
    public static final String CLOUD_EVENT_ACCOUNT_EXTENSION_KEY = "rh-account";
    public static final String CLOUD_EVENT_TYPE_PREFIX = "com.redhat.console.notification.toCamel.";
    public static final String CAMEL_SUBTYPE_HEADER = "CAMEL_SUBTYPE";
    public static final String PROCESSORNAME = "processorname";

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    BaseTransformer transformer;

    @Inject
    @Channel(TOCAMEL_CHANNEL)
    Emitter<String> emitter;

    @Inject
    MeterRegistry registry;

    @Inject
    Bridge bridge;

    @Inject
    BridgeAuth bridgeAuth;

    @Override
    public List<NotificationHistory> process(Event event, List<Endpoint> endpoints) {
        return endpoints.stream()
                .map(endpoint -> {
                    Notification notification = new Notification(event, endpoint);
                    return process(notification);
                })
                .collect(Collectors.toList());
    }

    private NotificationHistory process(Notification item) {
        Endpoint endpoint = item.getEndpoint();
        String subType = endpoint.getSubType();

        Counter processedCount = registry.counter(PROCESSED_COUNTER_NAME, "subType", subType);
        processedCount.increment();

        CamelProperties properties = (CamelProperties) endpoint.getProperties();

        Map<String, String> metaData = new HashMap<>();

        metaData.put("trustAll", String.valueOf(properties.getDisableSslVerification()));

        metaData.put("url", properties.getUrl());
        metaData.put("type", subType);

        if (properties.getSecretToken() != null && !properties.getSecretToken().isBlank()) {
            metaData.put(TOKEN_HEADER, properties.getSecretToken());
        }

        BasicAuthentication basicAuthentication = properties.getBasicAuthentication();
        if (basicAuthentication != null && basicAuthentication.getUsername() != null && basicAuthentication.getPassword() != null) {
            StringBuilder sb = new StringBuilder(basicAuthentication.getUsername());
            sb.append(":");
            sb.append(basicAuthentication.getPassword());
            String b64 = Base64Utils.encode(sb.toString());
            metaData.put("basicAuth", b64);
        }

        metaData.put("extras", new MapConverter().convertToDatabaseColumn(properties.getExtras()));
        String originalEventId = "-not provided-";
        if (item.getEvent().getId() != null) {
            originalEventId = item.getEvent().getId().toString();
        }
        metaData.put("_originalId", originalEventId);

        JsonObject payload = transformer.transform(item.getEvent().getAction());
        UUID historyId = UUID.randomUUID();

        JsonObject metadataAsJson = new JsonObject();
        payload.put(NOTIF_METADATA_KEY, metadataAsJson);
        metaData.forEach(metadataAsJson::put);

        return callCamel(item, historyId, payload, originalEventId);
    }

    public NotificationHistory callCamel(Notification item, UUID historyId, JsonObject payload, String originalEventId) {

        final long startTime = System.currentTimeMillis();

        Endpoint endpoint = item.getEndpoint();
        String accountId = endpoint.getAccountId();
        // the next could give a CCE, but we only come here when it is a camel endpoint anyway
        String subType = endpoint.getSubType();
        CamelProperties camelProperties = endpoint.getProperties(CamelProperties.class);
        String integrationName = endpoint.getName();

        if (subType.equals("slack")) { // OpenBridge
            long endTime;
            NotificationHistory history = getHistoryStub(endpoint, item.getEvent(), 0L, historyId);
            try {
                callOpenBridge(payload, historyId, accountId, camelProperties, integrationName, originalEventId);
                history.setInvocationResult(true);
            } catch (Exception e) {
                history.setInvocationResult(false);
                Map<String, Object> details = new HashMap<>();
                details.put("failure", e.getMessage());
                history.setDetails(details);
                Log.infof("SE: Sending event with historyId=%s and originalId=%s failed: %s ",
                        historyId, originalEventId, e.getMessage());
            } finally {
                endTime = System.currentTimeMillis();
            }
            history.setInvocationTime(endTime - startTime);
            return history;

        } else {
            reallyCallCamel(payload, historyId, accountId, subType, integrationName, originalEventId);
            final long endTime = System.currentTimeMillis();
            // We only create a basic stub. The FromCamel filler will update it later
            NotificationHistory history = getHistoryStub(endpoint, item.getEvent(), endTime - startTime, historyId);
            return history;
        }
    }

    public void reallyCallCamel(JsonObject body, UUID historyId, String accountId, String subType, String integrationName, String originalEventId) {

        TracingMetadata tracingMetadata = TracingMetadata.withPrevious(Context.current());
        Message<String> msg = Message.of(body.encode());
        msg = msg.addMetadata(OutgoingCloudEventMetadata.builder()
                .withId(historyId.toString())
                .withExtension(CLOUD_EVENT_ACCOUNT_EXTENSION_KEY, accountId)
                .withType(CLOUD_EVENT_TYPE_PREFIX + subType)
                .build()
        );
        msg = msg.addMetadata(OutgoingKafkaRecordMetadata.builder()
            .withHeaders(new RecordHeaders().add(MESSAGE_ID_HEADER, historyId.toString().getBytes(UTF_8))
                    .add(CAMEL_SUBTYPE_HEADER, subType.getBytes(UTF_8)))
                .build()
        );
        msg = msg.addMetadata(tracingMetadata);
        Log.infof("CA Sending for account=%s, historyId=%s, integration=%s, origId=%s",
                accountId, historyId, integrationName, originalEventId);
        emitter.send(msg);

    }

    private void callOpenBridge(JsonObject body, UUID id, String accountId, CamelProperties camelProperties, String integrationName, String originalEventId) {

        if (!featureFlipper.isObEnabled()) {
            Log.debug("Ob not enabled, doing nothing");
            return;
        }

        Map<String, String> extras = camelProperties.getExtras();

        Map<String, Object> ce = new HashMap<>();

        ce.put("id", id);
        ce.put("source", "notifications"); // Source of original event?
        ce.put("specversion", "1.0");
        ce.put("type", "myType"); // Type of original event?
        ce.put("rhaccount", accountId);
        ce.put(PROCESSORNAME, extras.get(PROCESSORNAME));
        ce.put("originaleventid", originalEventId);
        // TODO add dataschema

        Log.infof("SE: Sending Event with historyId=%s, processorName=%s, processorId=%s, integration=%s, origId=%s",
                id.toString(), extras.get(PROCESSORNAME), extras.get("processorId"), integrationName, originalEventId);

        body.remove(NOTIF_METADATA_KEY); // Not needed on OB
        ce.put("data", body);

        BridgeEventService evtSvc = RestClientBuilder.newBuilder()
                .baseUri(URI.create(bridge.getEndpoint()))
                .build(BridgeEventService.class);

        JsonObject payload = JsonObject.mapFrom(ce);
        evtSvc.sendEvent(payload, bridgeAuth.getToken());
    }


}
