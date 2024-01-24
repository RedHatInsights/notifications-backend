package com.redhat.cloud.notifications.db.converters;

import com.redhat.cloud.notifications.Base64Utils;
import com.redhat.cloud.notifications.models.BasicAuthenticationLegacy;
import io.vertx.core.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class BasicAuthenticationConverter implements AttributeConverter<BasicAuthenticationLegacy, String> {

    @Override
    public String convertToDatabaseColumn(BasicAuthenticationLegacy auth) {
        if (auth == null) {
            return null;
        } else {
            BasicAuthenticationLegacy encodedAuth = new BasicAuthenticationLegacy(auth.getUsername(), Base64Utils.encode(auth.getPassword()));
            return Json.encode(encodedAuth);
        }
    }

    @Override
    public BasicAuthenticationLegacy convertToEntityAttribute(String json) {
        if (json == null) {
            return null;
        } else {
            BasicAuthenticationLegacy auth = Json.decodeValue(json, BasicAuthenticationLegacy.class);
            auth.setPassword(Base64Utils.decode(auth.getPassword()));
            return auth;
        }
    }
}
