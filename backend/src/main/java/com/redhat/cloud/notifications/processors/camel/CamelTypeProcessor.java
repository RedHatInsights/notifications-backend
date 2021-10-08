package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.db.converters.MapConverter;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.context.Context;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.JsonObject;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class CamelTypeProcessor implements EndpointTypeProcessor {

    private static final String TOKEN_HEADER = "X-Insight-Token";

    @Inject
    BaseTransformer transformer;

    @Inject
    @Channel("toCamel")
    Emitter<String> emitter;

    private static final Logger LOGGER = Logger.getLogger(CamelTypeProcessor.class);

    @Inject
    MeterRegistry registry;

    private Counter processedCount;

    @PostConstruct
    public void init() {
        processedCount = registry.counter("processor.camel.processed");
    }

    @Override
    public Multi<NotificationHistory> process(Event event, List<Endpoint> endpoints) {
        return Multi.createFrom().iterable(endpoints)
                .onItem().transformToUniAndConcatenate(endpoint -> {
                    Notification notification = new Notification(event, endpoint);
                    return process(notification);
                });
    }

    private Uni<NotificationHistory> process(Notification item) {
        processedCount.increment();
        Endpoint endpoint = item.getEndpoint();
        CamelProperties properties = (CamelProperties) endpoint.getProperties();

        Map<String, String> metaData = new HashMap<>();

        metaData.put("trustAll", String.valueOf(properties.getDisableSslVerification()));

        metaData.put("url", properties.getUrl());
        metaData.put("type", properties.getSubType());

        if (properties.getSecretToken() != null && !properties.getSecretToken().isBlank()) {
            metaData.put(TOKEN_HEADER, properties.getSecretToken());
        }

        BasicAuthentication basicAuthentication = properties.getBasicAuthentication();
        if (basicAuthentication != null) {
            StringBuilder sb = new StringBuilder(basicAuthentication.getUsername());
            sb.append(":");
            sb.append(basicAuthentication.getPassword());
            String b64 = Base64.getEncoder().encodeToString(sb.toString().getBytes(UTF_8));
            metaData.put("basicAuth", b64);
        }

        metaData.put("extras", new MapConverter().convertToDatabaseColumn(properties.getExtras()));

        Uni<JsonObject> payload = transformer.transform(item.getEvent().getAction());
        UUID historyId = UUID.randomUUID();

        payload = payload.onItem().transform(json -> {
            JsonObject metadata = new JsonObject();
            json.put("notif-metadata",metadata);
            metaData.forEach(metadata::put);
            return json;
        });
        return callCamel(item, historyId, payload);
    }

    public Uni<NotificationHistory> callCamel(Notification item, UUID historyId, Uni<JsonObject> payload) {

        final long startTime = System.currentTimeMillis();

        String accountId = item.getEndpoint().getAccountId();
        // the next could give a CCE, but we only come here when it is a camel endpoint anyway
        String subType = item.getEndpoint().getProperties(CamelProperties.class).getSubType();

        return payload.onItem()

                .transformToUni(json -> reallyCallCamel(json, historyId, accountId, subType)
                        .onItem().transform(resp -> {
                            final long endTime = System.currentTimeMillis();
                            // We only create a basic stub. The FromCamel filler will update it later
                            NotificationHistory history = getHistoryStub(item, endTime - startTime, historyId);
                            return history;
                        })
                );
    }

    public Uni<Boolean> reallyCallCamel(JsonObject body, UUID historyId, String accountId, String subType) {


        TracingMetadata tracingMetadata = TracingMetadata.withPrevious(Context.current());
        Message<String> msg = Message.of(body.encode());
        msg = msg.addMetadata(OutgoingCloudEventMetadata.builder()
                .withId(historyId.toString())
                .withExtension("rh-account", accountId)
                .withType("com.redhat.console.notification.toCamel." + subType)
                .build()
        );
        msg = msg.addMetadata(OutgoingKafkaRecordMetadata.builder()
            .withHeaders(new RecordHeaders().add(MESSAGE_ID_HEADER, historyId.toString().getBytes(UTF_8))
                    .add("CAMEL_SUBTYPE",subType.getBytes(UTF_8) ))
                .build()
        );
        msg = msg.addMetadata(tracingMetadata);
        LOGGER.infof("Sending for account " + accountId + " and history id " + historyId);
        Message<String> finalMsg = msg;
        return Uni.createFrom().item(() -> {
            emitter.send(finalMsg);
            return true;
        });

    }

}
