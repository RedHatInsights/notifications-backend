package com.redhat.cloud.notifications.utils;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Decoder;
import com.redhat.cloud.notifications.ingress.Registry;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ActionParser {

    private final Registry registry = new Registry();
    private final Decoder decoder = new Decoder(registry);

    public Action fromJsonString(String actionJson) {
        try {
            return decoder.decode(actionJson);
        } catch (Exception e) {
            throw new RuntimeException("Action parsing failed for payload: " + actionJson, e);
        }
    }
}
