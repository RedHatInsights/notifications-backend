package com.redhat.cloud.notifications.models.filter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import io.quarkus.logging.Log;

/**
 * This filter can be used to dynamically filter out properties from a REST response at serialization time. Each class
 * that contains a property to filter out must be annotated with
 * {@link com.fasterxml.jackson.annotation.JsonFilter @JsonFilter(ApiResponseFilter.NAME)}.
 */
public class ApiResponseFilter extends SimpleBeanPropertyFilter {

    public static final String NAME = "ApiResponseFilter";

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {
        // All filtered classes must be declared here.
        if (pojo instanceof EventType) {
            EventType eventType = (EventType) pojo;
            // We want to prevent the serialization of a specific property so we have to find the corresponding writer.
            switch (writer.getName()) {
                case "application":
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
                    break;
                default:
                    // Do nothing.
                    break;
            }
        } else if (pojo instanceof BehaviorGroup) {
            BehaviorGroup behaviorGroup = (BehaviorGroup) pojo;
            switch (writer.getName()) {
                case "actions":
                    if (behaviorGroup.isFilterOutActions()) {
                        logFilterOut(BehaviorGroup.class.getName(), "actions");
                        // This will prevent the serialization of the property.
                        return;
                    }
                    break;
                case "bundle":
                    if (behaviorGroup.isFilterOutBundle()) {
                        logFilterOut(BehaviorGroup.class.getName(), "bundle");
                        // This will prevent the serialization of the property.
                        return;
                    }
                    break;
                case "behaviors":
                    if (behaviorGroup.isFilterOutBehaviors()) {
                        logFilterOut(BehaviorGroup.class.getName(), "behaviors");
                        // This will prevent the serialization of the property.
                        return;
                    }
                default:
                    // Do nothing.
                    break;
            }
        } else if (pojo instanceof InstantEmailTemplate) {
            InstantEmailTemplate instantEmailTemplate = (InstantEmailTemplate) pojo;
            switch (writer.getName()) {
                case "event_type":
                    if (instantEmailTemplate.isFilterOutEventType()) {
                        logFilterOut(InstantEmailTemplate.class.getName(), "event_type");
                        return;
                    }
                    break;
                case "subject_template":
                case "body_template":
                    if (instantEmailTemplate.isFilterOutTemplates()) {
                        logFilterOut(InstantEmailTemplate.class.getName(), "subject_template/body_template");
                        // This will prevent the serialization of the properties.
                        return;
                    }
                    break;
                default:
                    // Do nothing.
                    break;
            }
        } else if (pojo instanceof AggregationEmailTemplate) {
            AggregationEmailTemplate aggregationEmailTemplate = (AggregationEmailTemplate) pojo;
            switch (writer.getName()) {
                case "application":
                    if (aggregationEmailTemplate.isFilterOutApplication()) {
                        logFilterOut(AggregationEmailTemplate.class.getName(), "application");
                        return;
                    }
                    break;
                case "subject_template":
                case "body_template":
                    if (aggregationEmailTemplate.isFilterOutTemplates()) {
                        logFilterOut(AggregationEmailTemplate.class.getName(), "subject_template/body_template");
                        // This will prevent the serialization of the properties.
                        return;
                    }
                    break;
                default:
                    // Do nothing.
                    break;
            }
        }
        // The property was not filtered out, it will be serialized.
        writer.serializeAsField(pojo, jgen, provider);
    }

    private void logFilterOut(String className, String fieldName) {
        Log.tracef("Filtering out %s#%s from a JSON response", className, fieldName);
    }
}
