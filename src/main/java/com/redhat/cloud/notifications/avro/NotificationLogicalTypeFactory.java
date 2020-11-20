package com.redhat.cloud.notifications.avro;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.LogicalTypes.LogicalTypeFactory;
import org.apache.avro.Schema;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

@ApplicationScoped
@Startup
public class NotificationLogicalTypeFactory implements LogicalTypeFactory {

    @Override
    public LogicalType fromSchema(Schema schema) {
        return JsonObjectLogicalType.INSTANCE;
    }

    @Override
    public String getTypeName() {
        return JsonObjectLogicalType.JSON_OBJECT_LOGICAL_TYPE_NAME;
    }

    void onStart(@Observes StartupEvent ev) {
        LogicalTypes.register(this.getTypeName(), this);
    }
}
