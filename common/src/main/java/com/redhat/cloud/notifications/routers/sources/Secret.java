package com.redhat.cloud.notifications.routers.sources;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <p>Defines the model for a Secret on the Sources backend. See {@link SourcesService} for the OpenApi Specs that define
 * it, and some considerations when trying to store an encrypted secret.</p>
 * <p>All the fields, except for the {@link Secret#username} and {@link Secret#password} ones are ignored if they are
 * {@code NULL}, since Sources is very strict with non-existing keys in the payload: it will return a bad request for
 * those requests.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Secret {

    public static final String TYPE_BASIC_AUTH = "notifications-basic-authentication";
    public static final String TYPE_SECRET_TOKEN = "notifications-secret-token";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long id;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String name;

    /**
     * The secret's authentication type. In essence, this field is for Sources to help match certain authentications to
     * certain applications or source types. For example, it doesn't make sense to store an AWS ARN for an application
     * that is hosted on an Azure cloud. However, in our case, since we are not creating any sources or applications
     * for our secrets, we can simply have a custom authentication type.
     */
    @JsonProperty("authtype")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String authenticationType;
    public String username;
    public String password;

    /**
     * A JSON object for any extra information to be stored in the secret.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String extra;

    @JsonIgnore
    public boolean isUsernamePasswordEmpty() {
        return password.isBlank() && username.isBlank();
    }
}
