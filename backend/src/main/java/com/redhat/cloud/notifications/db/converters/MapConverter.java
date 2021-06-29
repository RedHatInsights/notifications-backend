package com.redhat.cloud.notifications.db.converters;

import io.vertx.core.json.JsonObject;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.Map;

/**
 * Serialize a generic Map into a Json-encoded String and vice-versa.
 */
@Converter
public class MapConverter implements AttributeConverter<Map<String, String>, String> {
    @Override
    public String convertToDatabaseColumn(Map<String, String> map) {
        if (map == null) {
            return null;
        }
        JsonObject jo = JsonObject.mapFrom(map);
        return jo.encode();
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String s) {
        if (s == null) {
            return null;
        }
        JsonObject jo = new JsonObject(s);
        return jo.mapTo(Map.class);
    }
}
