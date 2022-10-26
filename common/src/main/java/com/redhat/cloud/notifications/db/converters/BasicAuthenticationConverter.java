package com.redhat.cloud.notifications.db.converters;

import com.redhat.cloud.notifications.Base64Utils;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import io.vertx.core.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class BasicAuthenticationConverter implements AttributeConverter<BasicAuthentication, String> {

    @Override
    public String convertToDatabaseColumn(BasicAuthentication auth) {
        if (auth == null) {
            return null;
        } else {
            BasicAuthentication encodedAuth = new BasicAuthentication(auth.getUsername(), Base64Utils.encode(auth.getPassword()));
            return Json.encode(encodedAuth);
        }
    }

    @Override
    public BasicAuthentication convertToEntityAttribute(String json) {
        if (json == null) {
            return null;
        } else {
            BasicAuthentication auth = Json.decodeValue(json, BasicAuthentication.class);
            auth.setPassword(Base64Utils.decode(auth.getPassword()));
            return auth;
        }
    }
}
