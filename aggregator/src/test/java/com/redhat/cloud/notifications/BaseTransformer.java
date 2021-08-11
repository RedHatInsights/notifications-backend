package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import javax.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class BaseTransformer {

    public JsonObject transform(Action action) {
        return toJsonObject(action);
    }

    private JsonObject toJsonObject(Action action) {
        JsonObject message = new JsonObject();
        message.put("bundle", action.getBundle());
        message.put("application", action.getApplication());
        message.put("event_type", action.getEventType());
        message.put("account_id", action.getAccountId());
        message.put("timestamp", action.getTimestamp().toString());
        message.put("events", new JsonArray(action.getEvents().stream().map(event -> Map.of(
                "metadata", new JsonObject(serializeAvroSchema(event.getMetadata())),
                "payload", JsonObject.mapFrom(event.getPayload())
        )).collect(Collectors.toList())));
        message.put("context", action.getContext());

        return message;
    }

    private <T extends SpecificRecord> String serializeAvroSchema(T avroObject) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            JsonEncoder jsonEncoder = EncoderFactory.get().jsonEncoder(avroObject.getSchema(), baos);
            DatumWriter<T> writer = new SpecificDatumWriter<T>((Class<T>) avroObject.getClass());
            writer.write(avroObject, jsonEncoder);
            jsonEncoder.flush();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Avro serialization failed", exception);
        }

        // https://issues.apache.org/jira/browse/AVRO-1678
        String content = baos.toString(StandardCharsets.UTF_8);
        if (content.equals("") && avroObject.getSchema().getType().equals(Schema.Type.RECORD)) {
            return "{}";
        }

        return content;
    }
}
