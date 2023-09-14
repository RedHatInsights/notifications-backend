package com.redhat.cloud.notifications.templates;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

// This mapper is required to forward the HTTP 400 error message.
public class BadRequestExceptionMapper implements ResponseExceptionMapper<BadRequestException> {

    @Override
    public BadRequestException toThrowable(Response response) {
        String errorMessage = response.readEntity(String.class);
        if (errorMessage != null && !errorMessage.isEmpty()) {
            return new BadRequestException(errorMessage);
        } else {
            return new BadRequestException();
        }
    }
}
