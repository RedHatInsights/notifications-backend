package com.redhat.cloud.notifications.db.converters;

import io.vertx.core.json.JsonObject;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class JsonObjectConverter implements AttributeConverter<JsonObject, String> {

    @Override
    public String convertToDatabaseColumn(JsonObject jsonObject) {
        if (jsonObject == null) {
            return null;
        } else {
            return jsonObject.encode();
        }
    }

    @Override
    public JsonObject convertToEntityAttribute(String json) {
        if (json == null) {
            return null;
        } else {
            return new JsonObject(json);
        }
    }
}
