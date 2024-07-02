package com.redhat.cloud.notifications.recipients.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalAuthorizationCriteria {
    @NotNull
    private String assetType;

    @NotNull
    private String assetId;

    @NotNull
    private String relation;

    public ExternalAuthorizationCriteria(String assetType, String assetId, String relation) {
        this.assetType = assetType;
        this.assetId = assetId;
        this.relation = relation;
    }

    public @NotNull String getAssetType() {
        return assetType;
    }

    public @NotNull String getAssetId() {
        return assetId;
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
        ExternalAuthorizationCriteria that = (ExternalAuthorizationCriteria) o;
        return Objects.equals(assetType, that.assetType) && Objects.equals(assetId, that.assetId) && Objects.equals(relation, that.relation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetType, assetId, relation);
    }
}
