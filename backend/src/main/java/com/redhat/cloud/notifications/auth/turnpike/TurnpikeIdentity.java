package com.redhat.cloud.notifications.auth.turnpike;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * x-rh-identity header can have several identity 'payloads' depending on
 * who generates it. Turnpike currently has x509 and saml.
 * 3scale also has type User. We differentiate according to
 * the type property.
 * <p/>
 * See https://stackoverflow.com/a/62299710/100957,
 * https://fasterxml.github.io/jackson-annotations/javadoc/2.4/com/fasterxml/jackson/annotation/JsonTypeInfo.html and
 * https://medium.com/@david.truong510/jackson-polymorphic-deserialization-91426e39b96a
 * for the @JsonTypeInfo and @JsonSubTypes
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = X509Identity.class, name = "X509"),
        @JsonSubTypes.Type(value = SamlIdentity.class, name = "Associate"),
})
public abstract class TurnpikeIdentity {
    @JsonProperty(required = true)
    public String type;
    @JsonProperty
    public String auth_info;

    abstract public String getSubject();
}
