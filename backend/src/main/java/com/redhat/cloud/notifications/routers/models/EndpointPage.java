package com.redhat.cloud.notifications.routers.models;

import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;

import java.util.List;
import java.util.Map;

public class EndpointPage extends Page<EndpointDTO> {

    public EndpointPage() {
        super();
    }

    public EndpointPage(List<EndpointDTO> data, Map<String, String> links, Meta meta) {
        super(data, links, meta);
    }
}
