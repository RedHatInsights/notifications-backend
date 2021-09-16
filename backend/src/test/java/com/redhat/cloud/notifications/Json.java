package com.redhat.cloud.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redhat.cloud.notifications.models.filter.ApiResponseFilter;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/*
 * Before Quarkus 2, we used io.vertx.core.json.Json to (de)serialize RestAssured requests bodies. Since Quarkus 2, the
 * underlying ObjectMapper instance which is provided by QuarkusJacksonJsonCodec is no longer customizable. That's why
 * we need this class now.
 */
public class Json {

    private static ObjectMapper OBJECT_MAPPER = init();

    private static ObjectMapper init() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setFilterProvider(new SimpleFilterProvider().addFilter(ApiResponseFilter.NAME, new ApiResponseFilter()));
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    public static String encode(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Jackson serialization failed", e);
        }
    }

    public static <T> T decodeValue(String jsonValue, Class<T> decodedClass) {
        try {
            return OBJECT_MAPPER.readValue(jsonValue, decodedClass);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Jackson deserialization failed", e);
        }
    }
}
