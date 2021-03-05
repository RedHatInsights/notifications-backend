package com.redhat.cloud.notifications.db.entities;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "bundles")
public class BundleEntity extends CreationUpdateTimestampedEntity {

    @Id
    @GeneratedValue
    @NotNull
    public UUID id;

    @Column(unique = true)
    @NotNull
    @Size(max = 255)
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    public String name;

    @Column(name = "display_name")
    @NotNull
    public String displayName;

    @OneToMany(mappedBy = "bundle", cascade = CascadeType.REMOVE)
    public Set<ApplicationEntity> applications;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof BundleEntity) {
            BundleEntity other = (BundleEntity) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
