package com.redhat.cloud.notifications.avro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;

import java.util.Map;

public class JsonObjectConversion extends Conversion<Map> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Schema getRecommendedSchema() {
        return JsonObjectLogicalType.INSTANCE.addToSchema(Schema.create(Type.STRING));
    }

    @Override
    public Class<Map> getConvertedType() {
        return Map.class;
    }

    @Override
    public String getLogicalTypeName() {
        return JsonObjectLogicalType.JSON_OBJECT_LOGICAL_TYPE_NAME;
    }

    @Override
    public Map fromCharSequence(CharSequence value, Schema schema, LogicalType type) {
        try {
            return mapper.readValue(value.toString(), Map.class);
        } catch (JsonProcessingException jpe) {
            throw new IllegalArgumentException(
                    String.format("Unable to convert '%s' to JsonObject", value),
                    jpe
            );
        }
    }

    @Override
    public CharSequence toCharSequence(Map value, Schema schema, LogicalType type) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException jpe) {
            throw new IllegalArgumentException(
                    String.format("Unable to convert '%s' to String", value),
                    jpe
            );
        }
    }
}
