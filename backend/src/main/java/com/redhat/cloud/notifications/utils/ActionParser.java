package com.redhat.cloud.notifications.utils;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Decoder;
import com.redhat.cloud.notifications.ingress.Registry;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ActionParser {

    private final Registry registry = new Registry();
    private final Decoder decoder = new Decoder(registry);

    public Uni<Action> fromJsonString(String actionJson) {
        return Uni.createFrom().item(() -> decoder.decode(actionJson));
    }
}
