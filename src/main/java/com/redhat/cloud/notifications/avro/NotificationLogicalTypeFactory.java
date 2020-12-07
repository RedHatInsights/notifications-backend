package com.redhat.cloud.notifications.avro;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import org.apache.avro.LogicalTypes;
import org.apache.avro.LogicalTypes.LogicalTypeFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

@ApplicationScoped
@Startup
public class NotificationLogicalTypeFactory {

    void onStart(@Observes StartupEvent ev) {

        LogicalTypeFactory[] logicalTypeFactories = {new JsonObjectFactory(), new Iso8601Factory()};

        for (LogicalTypeFactory ltf : logicalTypeFactories) {
            LogicalTypes.register(ltf.getTypeName(), ltf);
        }
    }
}
