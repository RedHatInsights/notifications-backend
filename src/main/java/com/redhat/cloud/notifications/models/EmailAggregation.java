package com.redhat.cloud.notifications.models;

import io.vertx.core.json.JsonObject;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.UUID;

public class EmailAggregation {

    @NotNull
    private Integer id;

    @NotNull
    private String accountId;

    @NotNull
    private Date created;

    @NotNull
    private UUID applicationId;

    @NotNull
    private JsonObject payload;

    public EmailAggregation() {

    }

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

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public JsonObject getPayload() {
        return this.payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }
}
