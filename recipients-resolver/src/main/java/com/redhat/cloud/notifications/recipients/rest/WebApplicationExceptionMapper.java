package com.redhat.cloud.notifications.recipients.rest;

import com.fasterxml.jackson.core.JsonParseException;
import io.quarkus.logging.Log;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {


    /**
     * Map thrown Exceptions to Responses with appropriate status codes
     */
    @Override
    public Response toResponse(WebApplicationException exception) {
        Log.error(exception);
        if (exception instanceof BadRequestException) {
            return Response.status(BAD_REQUEST).entity(exception.getMessage()).build();
        }
        if (exception.getCause() != null && exception.getCause() instanceof JsonParseException) {
            JsonParseException jsonParseException = (JsonParseException) exception.getCause();
            return Response.status(BAD_REQUEST).entity(jsonParseException.getMessage()).build();
        }
        return Response.status(exception.getResponse().getStatus()).entity(exception.getMessage()).build();
    }
}
