package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Entity
@Table(name = "gateway_certificate")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GatewayCertificate {
    @Id
    @GeneratedValue
    private UUID id;

    @NotNull
    private String certificateData;

    @NotNull
    private String environment;

    @NotNull
    @Transient
    private String bundle;

    @NotNull
    @Transient
    private String application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    @JsonIgnore
    private Application gatewayCertificateApplication;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCertificateData() {
        return certificateData;
    }

    public void setCertificateData(String certificateData) {
        this.certificateData = certificateData;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public Application getGatewayCertificateApplication() {
        return gatewayCertificateApplication;
    }

    public void setGatewayCertificateApplication(Application gatewayCertificateApplication) {
        this.gatewayCertificateApplication = gatewayCertificateApplication;
    }
}
