package com.redhat.cloud.notifications.models.secrets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public abstract class Secret {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY, value = "is_present")
    protected boolean present;

    /**
     * Is the secret present in Sources?
     * @return {@code true} if the secret is present in Sources.
     */
    @JsonIgnore
    public boolean isPresent() {
        return this.present;
    }

    @JsonIgnore
    public abstract boolean isBlank();
}
