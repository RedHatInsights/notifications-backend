package com.redhat.cloud.notifications.routers.exception;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.apache.http.HttpStatus;

/**
 * Overrides the default exception mapper for the {@link ConstraintViolation}
 * exceptions. The reason is that if any method which isn't a top level handler
 * gets annotated with a {@link jakarta.validation.Valid} annotation, and the
 * incoming object is not valid, for some reason instead of capturing that
 * {@link ConstraintViolation} and returning a {@link jakarta.ws.rs.BadRequestException},
 * an {@link jakarta.ws.rs.InternalServerErrorException} is returned with a
 * {@link HttpStatus#SC_INTERNAL_SERVER_ERROR} status code. This exception
 * mapper avoids that and returns the proper response for any {@link ConstraintViolation}
 * that gets thrown in the code.
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(final ConstraintViolationException e) {
        final JsonObject responseBody = new JsonObject();

        // Describe the error in the payload.
        responseBody.put("title", "Constraint Violation");
        responseBody.put("description", "The submitted payload is incorrect");

        final JsonArray violations = new JsonArray();

        // Describe the fields that generated an error.
        for (final ConstraintViolation<?> constraintViolation : e.getConstraintViolations()) {
            final JsonObject violation = new JsonObject();

            violation.put("field", constraintViolation.getPropertyPath().toString());
            violation.put("message", constraintViolation.getMessage());

            violations.add(violation);
        }

        responseBody.put("violations", violations);

        // Return the response.
        return Response
            .status(HttpStatus.SC_BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(responseBody.encode())
            .build();
    }
}
