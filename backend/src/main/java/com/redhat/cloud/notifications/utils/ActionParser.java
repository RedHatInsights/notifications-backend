package com.redhat.cloud.notifications.utils;

import com.redhat.cloud.notifications.ingress.Action;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.specific.SpecificDatumReader;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;

@ApplicationScoped
public class ActionParser {

    public Action fromJsonString(String actionJson) {
        Action action = new Action();
        try {
            // Which ones can I reuse?
            JsonDecoder jsonDecoder = DecoderFactory.get().jsonDecoder(Action.getClassSchema(), actionJson);
            DatumReader<Action> reader = new SpecificDatumReader<>(Action.class);
            reader.read(action, jsonDecoder);
        } catch (IOException e) {
            throw new IllegalArgumentException("Action parsing from json failed", e);
        }
        return action;
    }
}
