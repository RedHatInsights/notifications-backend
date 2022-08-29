package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.db.converters.JsonObjectConverter;
import io.vertx.core.json.JsonObject;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;

@Entity
@Table(name = "email_aggregation")
public class EmailAggregation extends CreationTimestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "email_aggregation_id_seq")
    @JsonProperty(access = READ_ONLY)
    private Integer id;

    @Size(max = 50)
    private String accountId;

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

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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
