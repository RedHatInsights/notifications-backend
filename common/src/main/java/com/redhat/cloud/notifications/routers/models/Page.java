package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonSerialize
public class Page<T> {
    @NotNull
    private List<T> data;
    @NotNull
    private Map<String, String> links;
    @NotNull
    private Meta meta;

    public static final Page EMPTY_PAGE = new Page<>(Collections.emptyList(), Collections.emptyMap(), new Meta(0L));

    public Page() {

    }

    public Page(@NotNull List<T> data, @NotNull Map<String, String> links, @NotNull Meta meta) {
        this.data = data;
        this.links = links;
        this.meta = meta;
    }

    public List<T> getData() {
        return data;
    }

    public Map<String, String> getLinks() {
        return links;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public void setLinks(Map<String, String> links) {
        this.links = links;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }
}
