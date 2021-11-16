package com.redhat.cloud.notifications.utils;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Decoder;
import com.redhat.cloud.notifications.ingress.Registry;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ActionParser {

    private static final Logger LOGGER = Logger.getLogger(ActionParser.class);

    private final Registry registry = new Registry();
    private final Decoder decoder = new Decoder(registry);

    public Uni<Action> fromJsonString(String actionJson) {
        return Uni.createFrom().item(() -> decoder.decode(actionJson))
                .onFailure().invoke(e -> LOGGER.warnf(e, "Action parsing failed for payload: %s", actionJson));
    }
}
