package com.redhat.cloud.notifications.avro;

import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes.LogicalTypeFactory;
import org.apache.avro.Schema;

public class Iso8601Factory implements LogicalTypeFactory {

    @Override
    public LogicalType fromSchema(Schema schema) {
        return Iso8601LogicalType.INSTANCE;
    }

    @Override
    public String getTypeName() {
        return Iso8601LogicalType.ISO_8601_LOGICAL_TYPE_NAME;
    }
}
