package com.redhat.cloud.notifications.routers.models;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class RenderEmailTemplateRequest {

    @NotNull
    private String titleTemplate;

    @NotNull
    private String bodyTemplate;

    @NotEmpty
    private String payload;

    public String getTitleTemplate() {
        return titleTemplate;
    }

    public void setTitleTemplate(String titleTemplate) {
        this.titleTemplate = titleTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(String bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
