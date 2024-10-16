package com.redhat.cloud.notifications.models.dto.v1;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

/**
 * A bundle is an aggregation of applications.
 */
@JsonNaming(SnakeCaseStrategy.class)
public class BundleDTO {

    private UUID id;

    @NotNull
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    @Size(max = 255)
    private String name;

    @NotNull
    private String displayName;

    private Set<ApplicationDTO> applications;

    public BundleDTO() {
    }

    public BundleDTO(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Set<ApplicationDTO> getApplications() {
        return applications;
    }

    public void setApplications(Set<ApplicationDTO> applications) {
        this.applications = applications;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof BundleDTO) {
            BundleDTO other = (BundleDTO) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
