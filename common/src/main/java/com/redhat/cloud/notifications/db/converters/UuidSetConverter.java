package com.redhat.cloud.notifications.db.converters;

import io.vertx.core.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Serialize a Set<UUID> into a JSON-encoded String and vice-versa.
 */
@Converter
public class UuidSetConverter implements AttributeConverter<Set<UUID>, String> {

    @Override
    public String convertToDatabaseColumn(Set<UUID> uuidSet) {
        if (uuidSet == null || uuidSet.isEmpty()) {
            return null;
        }
        // Convert Set<UUID> to JSON array string
        return Json.encode(uuidSet);
    }

    @Override
    public Set<UUID> convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return Collections.emptySet();
        }
        // Decode JSON array to Set<UUID>
        UUID[] uuidArray = Json.decodeValue(jsonData, UUID[].class);
        return Set.of(uuidArray);
    }
}
