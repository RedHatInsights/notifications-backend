package com.redhat.cloud.notifications.routers.models;

public class RenderEmailTemplateResponse {

    private final String title;
    private final String body;

    public RenderEmailTemplateResponse(String title, String body) {
        this.title = title;
        this.body = body;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }
}
