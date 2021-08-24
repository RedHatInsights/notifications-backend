package com.redhat.cloud.notifications.utils;

import com.redhat.cloud.notifications.ingress.Action;
import io.smallrye.mutiny.Uni;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.specific.SpecificDatumReader;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;

@ApplicationScoped
public class ActionParser {

    public Uni<Action> fromJsonString(String actionJson) {
        return Uni.createFrom().item(() -> {
            Action action = new Action();
            try {
                // Which ones can I reuse?
                JsonDecoder jsonDecoder = DecoderFactory.get().jsonDecoder(Action.getClassSchema(), actionJson);
                DatumReader<Action> reader = new SpecificDatumReader<>(Action.class);
                reader.read(action, jsonDecoder);
            } catch (IOException e) {
                throw new UncheckedIOException("Action parsing from json failed", e);
            }
            return action;
        });
    }
}
