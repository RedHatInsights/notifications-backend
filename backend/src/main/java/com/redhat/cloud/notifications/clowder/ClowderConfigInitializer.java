package com.redhat.cloud.notifications.clowder;

import io.quarkus.runtime.StartupEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

@ApplicationScoped
public class ClowderConfigInitializer {

    void init(@Observes StartupEvent event) {
    }
}
