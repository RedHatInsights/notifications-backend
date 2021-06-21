package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.db.converters.MapConverter;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
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
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;

@ApplicationScoped
public class CamelTypeProcessor implements EndpointTypeProcessor {

    private static final String TOKEN_HEADER = "X-Insight-Token";

    @Inject
    BaseTransformer transformer;

    @Inject
    @Channel("toCamel")
    Emitter<String> emitter;

    @Inject
    MeterRegistry registry;

    private Counter processedCount;

    @PostConstruct
    public void init() {
        processedCount = registry.counter("processor.camel.processed");
    }

    @Override
    public Multi<NotificationHistory> process(Action action, List<Endpoint> endpoints) {
        return Multi.createFrom().iterable(endpoints)
                .onItem().transformToUniAndConcatenate(endpoint -> {
                    Notification notification = new Notification(action, endpoint);
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
            String b64 = Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
            metaData.put("basicAuth", b64);
        }

        metaData.put("extras", new MapConverter().convertToDatabaseColumn(properties.getExtras()));

        Uni<JsonObject> payload = transformer.transform(item.getAction());
        return callCamel(item, metaData, payload);
    }

    public Uni<NotificationHistory> callCamel(Notification item, Map<String, String> meta, Uni<JsonObject> payload) {

        final long startTime = System.currentTimeMillis();
        final Map<String, Object> tmp = new HashMap<>();
        UUID historyId = UUID.randomUUID();
        meta.put("historyId", historyId.toString());

        Uni<NotificationHistory> historyUni = payload.onItem()
                .transform(json -> {
                    tmp.put("meta", meta);
                    tmp.put("payload", json);
                    return tmp;
                })
                .onItem().transformToUni(json -> reallyCallCamel(json)
                        .onItem().transform(resp -> {
                            final long endTime = System.currentTimeMillis();
                            // We only create a basic stub. The FromCamel filler will update it later
                            NotificationHistory history = getHistoryStub(item, endTime - startTime, historyId);
                            return history;
                        })
                );

        return historyUni;
    }

    public Uni<Boolean> reallyCallCamel(Map<String, Object> body) {

        JsonObject jo = JsonObject.mapFrom(body);

        TracingMetadata tracingMetadata = TracingMetadata.withPrevious(Context.current());
        Message<String> msg = Message.of(jo.encode()); //, Metadata.of(tracingMetadata));

        return Uni.createFrom().item(() -> {
            emitter.send(jo.encode()); // TODO use msg
            return true;
        });

    }

}
