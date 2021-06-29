package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.JsonParseException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Provider
public class JsonParseExceptionMapper implements ExceptionMapper<JsonParseException> {

    @Override
    public Response toResponse(JsonParseException exception) {
        return Response.status(BAD_REQUEST).entity(exception.getMessage()).build();
    }
}
