package com.redhat.cloud.notifications.models.filter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.EventType;
import org.jboss.logging.Logger;

/**
 * This filter can be used to dynamically filter out properties from a REST response at serialization time. Each class
 * that contains a property to filter out must be annotated with
 * {@link com.fasterxml.jackson.annotation.JsonFilter @JsonFilter(ApiResponseFilter.NAME)}.
 */
public class ApiResponseFilter extends SimpleBeanPropertyFilter {

    public static final String NAME = "ApiResponseFilter";

    private static final Logger LOGGER = Logger.getLogger(ApiResponseFilter.class);

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {
        // All filtered classes must be declared here.
        if (pojo instanceof EventType) {
            EventType eventType = (EventType) pojo;
            // We want to prevent the serialization of a specific property so we have to find the corresponding writer.
            // Do nothing.
            if ("application".equals(writer.getName())) {
             /*
             * This is how the filter behaves dynamically: we need to add a boolean attribute (never serialized
             * or persisted) in the class which will indicate whether or not the property should be filtered
             * out. That way, it is possible to filter a property from the REST response of API "A" and include
             * the same property into the REST response of API "B".
             */
                if (eventType.isFilterOutApplication()) {
                    logFilterOut(EventType.class.getName(), "application");
                    // This will prevent the serialization of the property.
                    return;
                }
            }
        } else if (pojo instanceof BehaviorGroup) {
            BehaviorGroup behaviorGroup = (BehaviorGroup) pojo;
            // Do nothing.
            if ("actions".equals(writer.getName())) {
                if (behaviorGroup.isFilterOutActions()) {
                    logFilterOut(BehaviorGroup.class.getName(), "actions");
                    // This will prevent the serialization of the property.
                    return;
                }
            }
        }
        // The property was not filtered out, it will be serialized.
        writer.serializeAsField(pojo, jgen, provider);
    }

    private void logFilterOut(String className, String fieldName) {
        LOGGER.debugf("Filtering out %s#%s from a JSON response", className, fieldName);
    }
}
