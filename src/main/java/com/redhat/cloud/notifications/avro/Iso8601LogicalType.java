package com.redhat.cloud.notifications.avro;

import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;

public class Iso8601LogicalType extends LogicalType {

    static final String ISO_8601_LOGICAL_TYPE_NAME = "iso-8601";
    static final Iso8601LogicalType INSTANCE = new Iso8601LogicalType();

    private Iso8601LogicalType() {
        super(ISO_8601_LOGICAL_TYPE_NAME);
    }

    @Override
    public void validate(Schema schema) {
        super.validate(schema);
        if (!schema.getType().equals(Type.STRING)) {
            throw new IllegalArgumentException(
                    String.format("Logical type '%s' must be backed by string", ISO_8601_LOGICAL_TYPE_NAME)
            );
        }
    }
}
