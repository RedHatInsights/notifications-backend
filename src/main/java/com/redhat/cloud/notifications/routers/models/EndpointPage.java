package com.redhat.cloud.notifications.routers.models;

import com.redhat.cloud.notifications.models.Endpoint;

import java.util.List;
import java.util.Map;

public class EndpointPage extends Page<Endpoint> {

    public EndpointPage(List<Endpoint> data, Map<String, String> links, Meta meta) {
        super(data, links, meta);
    }
}
