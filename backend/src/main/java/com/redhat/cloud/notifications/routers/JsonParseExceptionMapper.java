package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.JsonParseException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

/*
 * This exception mapper doesn't seem to be used anymore starting Quarkus 2.15.1.Final.
 * Since that change was not documented by the Quarkus team, let's keep this class around
 * for a while in case the new behavior would be rolled back in the near future. The old
 * logic below has been moved to JaxRsExceptionMapper.
 * TODO Remove this class later, AFTER checking that its method is never called.
 */
@Provider
public class JsonParseExceptionMapper implements ExceptionMapper<JsonParseException> {

    @Override
    public Response toResponse(JsonParseException exception) {
        return Response.status(BAD_REQUEST).entity(exception.getMessage()).build();
    }
}
