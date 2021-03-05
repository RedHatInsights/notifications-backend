package com.redhat.cloud.notifications.db.converters;

import com.redhat.cloud.notifications.models.HttpType;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class HttpTypeConverter implements AttributeConverter<HttpType, String> {

    @Override
    public String convertToDatabaseColumn(HttpType type) {
        if (type == null) {
            return null;
        } else {
            return type.name();
        }
    }

    @Override
    public HttpType convertToEntityAttribute(String name) {
        if (name == null) {
            return null;
        } else {
            return HttpType.valueOf(name);
        }
    }
}
