package com.redhat.cloud.notifications.processors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.vertx.core.json.JsonObject;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalAuthorizationCriterion {

    @NotNull
    private JsonObject type;

    @NotNull
    private String id;

    @NotNull
    private String relation;

    public ExternalAuthorizationCriterion(JsonObject type, String id, String relation) {
        this.type = type;
        this.id = id;
        this.relation = relation;
    }

    public @NotNull JsonObject getType() {
        return type;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getRelation() {
        return relation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExternalAuthorizationCriterion that = (ExternalAuthorizationCriterion) o;
        return Objects.equals(type, that.type) && Objects.equals(id, that.id) && Objects.equals(relation, that.relation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id, relation);
    }
}
