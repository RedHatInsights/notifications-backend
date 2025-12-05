package com.redhat.cloud.notifications.templates.models;

import java.util.List;

public class DailyDigestSection {
    String body;
    List<String> headerLink;

    public DailyDigestSection(String body, List<String> headerLink) {
        this.body = body;
        this.headerLink = headerLink;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<String> getHeaderLink() {
        return headerLink;
    }

    public void setHeaderLink(List<String> headerLink) {
        this.headerLink = headerLink;
    }
}
