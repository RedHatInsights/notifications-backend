package com.redhat.cloud.notifications.openbridge;

import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

public class RhoseResponseExceptionMapper implements ResponseExceptionMapper<RuntimeException> {

    @Override
    public RuntimeException toThrowable(Response response) {
        switch (response.getStatus()) {
            case 400:
                String entity = response.readEntity(String.class);
                if (entity != null && !entity.isBlank()) {
                    return new WebApplicationException(
                            String.format("%s, status code %d %s", response.getStatusInfo().getReasonPhrase(), response.getStatus(), entity),
                            response);
                } else {
                    return getDefaultException(response);
                }
            default:
                return getDefaultException(response);
        }
    }

    // Default behavior from DefaultMicroprofileRestClientExceptionMapper.
    private WebApplicationException getDefaultException(Response response) {
        return new WebApplicationException(
                String.format("%s, status code %d", response.getStatusInfo().getReasonPhrase(), response.getStatus()),
                response);
    }
}
