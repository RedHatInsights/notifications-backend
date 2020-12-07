package com.redhat.cloud.notifications.avro;

import org.apache.avro.LogicalType;
import org.apache.avro.Schema;

public class JsonObjectLogicalType extends LogicalType {

    static final String JSON_OBJECT_LOGICAL_TYPE_NAME = "json-object";
    static final JsonObjectLogicalType INSTANCE = new JsonObjectLogicalType();

    private JsonObjectLogicalType() {
        super(JSON_OBJECT_LOGICAL_TYPE_NAME);
    }

    @Override
    public void validate(Schema schema) {
        super.validate(schema);

        if (!schema.getType().equals(Schema.Type.STRING)) {
            throw new IllegalArgumentException(
                    String.format("Logical type '%s' must be backed by string", JSON_OBJECT_LOGICAL_TYPE_NAME)
            );
        }
    }
}
