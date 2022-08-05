package com.redhat.cloud.notifications.orgid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrgIdResponse {
    public String id;
}
