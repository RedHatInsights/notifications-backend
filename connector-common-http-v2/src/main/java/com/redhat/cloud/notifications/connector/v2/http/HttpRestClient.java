package com.redhat.cloud.notifications.connector.v2.http;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient
public interface HttpRestClient {

    @POST
    @Path("{path}")
    Response post(@PathParam("path") String path, String body);
}
