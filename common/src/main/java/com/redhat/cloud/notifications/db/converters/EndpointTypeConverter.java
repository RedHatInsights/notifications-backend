package com.redhat.cloud.notifications.db.converters;

import com.redhat.cloud.notifications.models.EndpointType;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class EndpointTypeConverter implements AttributeConverter<EndpointType, String> {

    @Override
    public String convertToDatabaseColumn(EndpointType type) {
        if (type == null) {
            return null;
        } else {
            return type.name();
        }
    }

    @Override
    public EndpointType convertToEntityAttribute(String name) {
        if (name == null) {
            return null;
        } else {
            try {
                return EndpointType.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Unknown EndpointType " + name);
            }
        }
    }
}
