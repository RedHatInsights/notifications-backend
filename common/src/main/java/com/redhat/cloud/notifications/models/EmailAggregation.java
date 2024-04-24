package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.db.converters.JsonObjectConverter;
import io.vertx.core.json.JsonObject;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static jakarta.persistence.GenerationType.SEQUENCE;

@Entity
@Table(name = "email_aggregation")
public class EmailAggregation extends CreationTimestamped {

    private static final String SEQUENCE_GENERATOR = "email-aggregation-sequence-generator";

    @Id
    @GeneratedValue(strategy = SEQUENCE, generator = SEQUENCE_GENERATOR)
    @SequenceGenerator(name = SEQUENCE_GENERATOR, sequenceName = "email_aggregation_id_seq")
    @JsonProperty(access = READ_ONLY)
    private Integer id;

    @NotNull
    @Size(max = 50)
    private String orgId;

    @NotNull
    @Size(max = 255)
    @Column(name = "bundle")
    @JsonProperty("bundle")
    private String bundleName;

    @NotNull
    @Size(max = 255)
    @Column(name = "application")
    @JsonProperty("application")
    private String applicationName;

    @NotNull
    @Convert(converter = JsonObjectConverter.class)
    private JsonObject payload;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EmailAggregation) {
            EmailAggregation other = (EmailAggregation) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
