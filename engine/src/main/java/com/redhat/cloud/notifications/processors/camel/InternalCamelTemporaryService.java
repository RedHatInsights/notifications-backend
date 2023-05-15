package com.redhat.cloud.notifications.processors.camel;

import org.eclipse.microprofile.faulttolerance.Retry;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public interface InternalCamelTemporaryService {

    @POST
    @Consumes(APPLICATION_JSON)
    @Retry
    void send(@NotNull CamelNotification notification);
}
