package com.redhat.cloud.notifications.avro;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Iso8601Conversion extends Conversion<LocalDateTime> {

    @Override
    public Schema getRecommendedSchema() {
        return Iso8601LogicalType.INSTANCE.addToSchema(Schema.create(Type.STRING));
    }

    @Override
    public Class<LocalDateTime> getConvertedType() {
        return LocalDateTime.class;
    }

    @Override
    public String getLogicalTypeName() {
        return Iso8601LogicalType.ISO_8601_LOGICAL_TYPE_NAME;
    }

    @Override
    public LocalDateTime fromCharSequence(CharSequence value, Schema schema, LogicalType type) {
        return LocalDateTime.parse(value.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Override
    public CharSequence toCharSequence(LocalDateTime value, Schema schema, LogicalType type) {
        return value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
