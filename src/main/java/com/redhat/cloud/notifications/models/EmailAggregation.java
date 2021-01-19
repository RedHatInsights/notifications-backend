package com.redhat.cloud.notifications.models;

import io.vertx.core.json.JsonObject;

import javax.validation.constraints.NotNull;
import java.util.Date;

public class EmailAggregation {

    @NotNull
    private Integer id;

    @NotNull
    private String accountId;

    @NotNull
    private Date created;

    @NotNull
    private String application;

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

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public JsonObject getPayload() {
        return this.payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }
}
