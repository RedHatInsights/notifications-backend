package com.redhat.cloud.notifications.db.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.DayOfWeek;

@Converter
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Integer> {

    @Override
    public Integer convertToDatabaseColumn(DayOfWeek day) {
        if (day == null) {
            return null;
        }
        return day.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Integer value) {
        if (value == null) {
            return null;
        }
        return DayOfWeek.of(value);
    }
}
