package com.redhat.cloud.notifications.auth.kessel.exception;

import io.grpc.StatusRuntimeException;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.apache.http.HttpStatus;

/**
 * Handles Kessel runtime exceptions by omitting the exception details from the
 * response's body, and by returning a proper "internal server error" response
 * to the client instead. The implementation follows the "Exception Mapping
 * Providers" guidelines from the <a href="https://jakarta.ee/specifications/restful-ws/3.1/jakarta-restful-ws-spec-3.1.html#exceptionmapper">
 * Jakarta specification</a>.
 */
@Provider
public class KesselExceptionMapper implements ExceptionMapper<StatusRuntimeException> {

    /**
     * Maps the {@link StatusRuntimeException} to an internal server error
     * response, hiding the details of the exception from the body to the
     * client.
     * @param e the {@link StatusRuntimeException} to be mapped.
     * @return a response with a {@link HttpStatus#SC_INTERNAL_SERVER_ERROR}
     * status code and a JSON message stating that an internal server error
     * occurred.
     */
    @Override
    public Response toResponse(final StatusRuntimeException e) {
        final JsonObject payload = new JsonObject();
        payload.put("error", "Internal server error");

        return Response
            .status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
            .type(MediaType.APPLICATION_JSON)
            .entity(payload.encode())
            .build();
    }
}
