package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.validation.constraints.NotNull;

@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class Meta {
    @NotNull
    private Long count;

    public Meta() {

    }

    public Meta(@NotNull Long count) {
        this.count = count;
    }

    public Long getCount() {
        return this.count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
