package com.redhat.cloud.notifications.db.entities;

import javax.persistence.Column;
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

@Entity
@Table(name = "endpoint_webhooks")
public class EndpointWebhookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "endpoint_webhooks_id_seq")
    @NotNull
    public Integer id;

    @OneToOne
    @JoinColumn(name = "endpoint_id")
    @NotNull
    public EndpointEntity endpoint;

    @NotNull
    public String url;

    @NotNull
    @Size(max = 10)
    public String method;

    @Column(name = "disable_ssl_verification")
    @NotNull
    public Boolean disableSslVerification;

    @Column(name = "secret_token")
    @Size(max = 255)
    public String secretToken;

    @Column(name = "basic_authentication")
    public String basicAuthentication;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointWebhookEntity) {
            EndpointWebhookEntity other = (EndpointWebhookEntity) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
