package com.redhat.cloud.notifications.db.entities;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "applications")
public class ApplicationEntity extends CreationUpdateTimestampedEntity {

    @Id
    @GeneratedValue
    @NotNull
    public UUID id;

    @NotNull
    @Size(max = 255)
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    public String name;

    @Column(name = "display_name")
    @NotNull
    public String displayName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bundle_id")
    @NotNull
    public BundleEntity bundle;

    @OneToMany(mappedBy = "application", cascade = CascadeType.REMOVE)
    public Set<EventTypeEntity> eventTypes;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof ApplicationEntity) {
            ApplicationEntity other = (ApplicationEntity) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
