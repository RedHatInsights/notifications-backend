package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.JsonParseException;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

/**
 * Map thrown Exceptions to Responses with appropriate status codes
 */
@Provider
public class JaxRsExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        if (exception instanceof BadRequestException) {
            return Response.status(BAD_REQUEST).entity(exception.getMessage()).build();
        }
        // JsonParseException is no longer intercepted directly by exception mappers with Quarkus 2.15.1.Final.
        if (exception.getCause() != null && exception.getCause() instanceof JsonParseException) {
            JsonParseException jsonParseException = (JsonParseException) exception.getCause();
            return Response.status(BAD_REQUEST).entity(jsonParseException.getMessage()).build();
        }
        return Response.status(exception.getResponse().getStatus()).entity(exception.getMessage()).build();
    }
}
