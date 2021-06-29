package com.redhat.cloud.notifications.db.converters;

import com.redhat.cloud.notifications.models.Status;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class StatusConverter implements AttributeConverter<Status, String> {

    @Override
    public String convertToDatabaseColumn(Status status) {
        if (status == null) {
            return null;
        } else {
            return status.name();
        }
    }

    @Override
    public Status convertToEntityAttribute(String name) {
        if (name == null) {
            return null;
        } else {
            return Status.valueOf(name);
        }
    }
}
