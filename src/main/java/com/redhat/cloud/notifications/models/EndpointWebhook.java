package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.BasicAuthenticationConverter;
import com.redhat.cloud.notifications.db.converters.HttpTypeConverter;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@Entity
@Table(name = "endpoint_webhooks")
@JsonNaming(SnakeCaseStrategy.class)
public class EndpointWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "endpoint_webhooks_id_seq")
    @NotNull
    private Integer id;

    @NotNull
    @OneToOne
    @JoinColumn(name = "endpoint_id")
    private Endpoint endpoint;

    @NotNull
    private String url;

    @NotNull
    @Convert(converter = HttpTypeConverter.class)
    private HttpType method;

    @NotNull
    private Boolean disableSslVerification;

    @Size(max = 255)
    private String secretToken;

    @Convert(converter = BasicAuthenticationConverter.class)
    private BasicAuthentication basicAuthentication;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public HttpType getMethod() {
        return method;
    }

    public void setMethod(HttpType method) {
        this.method = method;
    }

    public Boolean getDisableSslVerification() {
        return disableSslVerification;
    }

    public void setDisableSslVerification(Boolean disableSslVerification) {
        this.disableSslVerification = disableSslVerification;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }

    public BasicAuthentication getBasicAuthentication() {
        return basicAuthentication;
    }

    public void setBasicAuthentication(BasicAuthentication basicAuthentication) {
        this.basicAuthentication = basicAuthentication;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointWebhook) {
            EndpointWebhook other = (EndpointWebhook) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
