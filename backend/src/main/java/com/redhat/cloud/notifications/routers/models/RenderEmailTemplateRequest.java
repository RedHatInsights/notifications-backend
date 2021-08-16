package com.redhat.cloud.notifications.routers.models;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class RenderEmailTemplateRequest {

    @NotNull
    private String subjectTemplate;

    @NotNull
    private String bodyTemplate;

    @NotEmpty
    private String payload;

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public void setSubjectTemplate(String subjectTemplate) {
        this.subjectTemplate = subjectTemplate;
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
