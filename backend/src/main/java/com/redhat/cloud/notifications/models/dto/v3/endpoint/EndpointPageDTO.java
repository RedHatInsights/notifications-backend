package com.redhat.cloud.notifications.models.dto.v3.endpoint;

import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;

import java.util.List;
import java.util.Map;

public class EndpointPageDTO extends Page<EndpointDTO> {

    public EndpointPageDTO() {
        super();
    }

    public EndpointPageDTO(List<EndpointDTO> data, Map<String, String> links, Meta meta) {
        super(data, links, meta);
    }
}
