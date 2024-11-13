package com.redhat.cloud.notifications.processors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalAuthorizationCriterion {

    @NotNull
    private Type type;

    @NotNull
    private String id;

    @NotNull
    private String relation;

    public ExternalAuthorizationCriterion(Type type, String id, String relation) {
        this.type = type;
        this.id = id;
        this.relation = relation;
    }

    public @NotNull Type getType() {
        return type;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getRelation() {
        return relation;
    }

    public static class Type {
        public String name;
        public String namespace;

        public Type(String name, String namespace) {
            this.name = name;
            this.namespace = namespace;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Type type = (Type) o;
            return Objects.equals(name, type.name) && Objects.equals(namespace, type.namespace);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, namespace);
        }
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
        return Objects.hash(type.hashCode(), id, relation);
    }
}
