package com.redhat.cloud.notifications.db.converters;

import com.redhat.cloud.notifications.EmailSubscriptionType;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class EmailSubscriptionTypeConverter implements AttributeConverter<EmailSubscriptionType, String> {

    @Override
    public String convertToDatabaseColumn(EmailSubscriptionType type) {
        return type == null ? null : type.name();
    }

    @Override
    public EmailSubscriptionType convertToEntityAttribute(String name) {
        if (name == null) {
            return null;
        }
        try {
            return EmailSubscriptionType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown EmailSubscriptionType " + name);
        }
    }
}
