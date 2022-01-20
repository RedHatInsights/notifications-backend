package com.redhat.cloud.notifications.db.converters;

import com.redhat.cloud.notifications.models.BasicAuthentication;
import io.vertx.core.json.Json;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class BasicAuthenticationConverter implements AttributeConverter<BasicAuthentication, String> {

    @Override
    public String convertToDatabaseColumn(BasicAuthentication auth) {
        if (auth == null) {
            return null;
        } else {
            return Json.encode(auth);
        }
    }

    @Override
    public BasicAuthentication convertToEntityAttribute(String json) {
        if (json == null) {
            return null;
        } else {
            return Json.decodeValue(json, BasicAuthentication.class);
        }
    }
}
