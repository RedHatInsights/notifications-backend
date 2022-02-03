package com.redhat.cloud.notifications.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.redhat.cloud.notifications.auth.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.turnpike.TurnpikeSamlIdentity;
import com.redhat.cloud.notifications.auth.turnpike.TurnpikeX509Identity;

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
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RhIdentity.class, name = "User"),
        @JsonSubTypes.Type(value = TurnpikeX509Identity.class, name = "X509"),
        @JsonSubTypes.Type(value = TurnpikeSamlIdentity.class, name = "Associate"),
})
public abstract class ConsoleDotIdentity {
    @JsonProperty(required = true)
    public String type;

    public abstract String getName();
}
