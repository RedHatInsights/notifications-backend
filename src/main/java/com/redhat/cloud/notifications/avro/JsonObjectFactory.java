package com.redhat.cloud.notifications.avro;

import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes.LogicalTypeFactory;
import org.apache.avro.Schema;

public class JsonObjectFactory implements LogicalTypeFactory {

    @Override
    public LogicalType fromSchema(Schema schema) {
        return JsonObjectLogicalType.INSTANCE;
    }

    @Override
    public String getTypeName() {
        return JsonObjectLogicalType.JSON_OBJECT_LOGICAL_TYPE_NAME;
    }

}
