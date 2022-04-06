package com.redhat.cloud.notifications.processors.email;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/test")
public class EmailResource {

    @Inject
    EmailSender emailSender;

    @GET
    public String doSomething() {
        emailSender.exposeMetric();
        return "blabla";
    }
}
