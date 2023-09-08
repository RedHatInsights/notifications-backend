package com.redhat.cloud.notifications.models.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

@Singleton
public class ApiObjectMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        FilterProvider filterProvider = new SimpleFilterProvider().addFilter(ApiResponseFilter.NAME, new ApiResponseFilter());
        mapper.setFilterProvider(filterProvider);
    }
}
