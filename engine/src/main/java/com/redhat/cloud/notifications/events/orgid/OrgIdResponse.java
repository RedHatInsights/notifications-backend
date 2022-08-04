package com.redhat.cloud.notifications.events.orgid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrgIdResponse {
    public String id;
}
