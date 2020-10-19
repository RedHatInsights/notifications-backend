package com.redhat.cloud.notifications.routers;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Map thrown Exceptions to Responses with appropriate status codes
 */
@Provider
public class JaxRsExceptionMapper implements ExceptionMapper<BadRequestException> {
    @Override
    public Response toResponse(BadRequestException exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.getMessage()).build();
    }
}
