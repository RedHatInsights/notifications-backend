package com.redhat.cloud.notifications.db.converters;

import io.vertx.core.json.Json;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.Map;

@Converter
public class NotificationHistoryDetailsConverter implements AttributeConverter<Map<String, Object>, String> {

    @Override
    public String convertToDatabaseColumn(Map<String, Object> details) {
        if (details == null) {
            return null;
        } else {
            return Json.encode(details);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String details) {
        if (details == null) {
            return null;
        } else {
            return Json.decodeValue(details, Map.class);
        }
    }
}
