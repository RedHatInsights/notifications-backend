package com.redhat.cloud.notifications.db.converters;

import com.redhat.cloud.notifications.models.SubscriptionType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SubscriptionTypeConverter implements AttributeConverter<SubscriptionType, String> {

    @Override
    public String convertToDatabaseColumn(SubscriptionType type) {
        if (type == null) {
            return null;
        } else {
            return type.name();
        }
    }

    @Override
    public SubscriptionType convertToEntityAttribute(String name) {
        if (name == null) {
            return null;
        } else {
            try {
                return SubscriptionType.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Unknown SubscriptionType " + name);
            }
        }
    }
}
