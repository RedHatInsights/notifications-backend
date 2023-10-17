package com.redhat.cloud.notifications.recipients.resolver.rbac;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.constraints.NotNull;

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
