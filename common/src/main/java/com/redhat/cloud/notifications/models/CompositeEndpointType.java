package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.redhat.cloud.notifications.db.converters.EndpointTypeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;

@Embeddable
public class CompositeEndpointType {

    @NotNull
    @Column(name = "endpoint_type_v2")
    @Convert(converter = EndpointTypeConverter.class)
    private EndpointType type;

    @Column(name = "endpoint_sub_type")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Size(max = 20)
    private String subType;

    public static CompositeEndpointType fromString(String type) {
        String[] pieces = type.split(":", 2);
        if (pieces.length == 1) {
            return new CompositeEndpointType(EndpointType.valueOf(type.toUpperCase()));
        } else {
            return new CompositeEndpointType(EndpointType.valueOf(pieces[0].toUpperCase()), pieces[1]);
        }
    }

    public CompositeEndpointType() {
    }

    public CompositeEndpointType(EndpointType type) {
        this.type = type;
    }

    public CompositeEndpointType(EndpointType type, String subType) {
        this.type = type;
        this.subType = subType.toLowerCase();
    }

    public EndpointType getType() {
        return type;
    }

    public void setType(EndpointType type) {
        this.type = type;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType == null ? null : subType.toLowerCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CompositeEndpointType that = (CompositeEndpointType) o;
        return type == that.type && Objects.equals(subType, that.subType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, subType);
    }
}
