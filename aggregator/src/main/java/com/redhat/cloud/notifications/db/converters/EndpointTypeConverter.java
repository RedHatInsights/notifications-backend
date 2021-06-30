package com.redhat.cloud.notifications.db.converters;

import com.redhat.cloud.notifications.models.EndpointType;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class EndpointTypeConverter implements AttributeConverter<EndpointType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(EndpointType type) {
        if (type == null) {
            return null;
        } else {
            return type.ordinal();
        }
    }

    @Override
    public EndpointType convertToEntityAttribute(Integer ordinal) {
        if (ordinal == null) {
            return null;
        } else {
            return EndpointType.values()[ordinal];
        }
    }
}
