package com.redhat.cloud.notifications.models.converter;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class EmailSubscriptionTypeConverter implements AttributeConverter<EmailSubscriptionType, String> {

    @Override
    public String convertToDatabaseColumn(EmailSubscriptionType type) {
        if (type == null) {
            return null;
        } else {
            return type.name();
        }
    }

    @Override
    public EmailSubscriptionType convertToEntityAttribute(String name) {
        if (name == null) {
            return null;
        } else {
            try {
                return EmailSubscriptionType.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Unknown EmailSubscriptionType " + name);
            }
        }
    }
}
