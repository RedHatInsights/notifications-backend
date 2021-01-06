package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.validation.constraints.NotNull;

@JsonSerialize
public class Meta {
    @NotNull
    private Integer count;

    public Meta(@NotNull Integer count) {
        this.count = count;
    }

    public Integer getCount() {
        return this.count;
    }
}
