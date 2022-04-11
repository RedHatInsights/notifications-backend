package com.redhat.cloud.notifications.routers;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Map thrown Exceptions to Responses with appropriate status codes
 */
@Provider
public class JaxRsExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        if (exception instanceof BadRequestException) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.getMessage()).build();
        }
        return Response.status(exception.getResponse().getStatus()).entity(exception.getMessage()).build();
    }
}
