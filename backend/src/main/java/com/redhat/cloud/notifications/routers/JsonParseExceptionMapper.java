package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.JsonParseException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

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
