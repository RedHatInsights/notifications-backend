package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.v2.MessageContext;
import com.redhat.cloud.notifications.connector.v2.http.HttpExceptionProcessor;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class DrawerExceptionProcessor extends HttpExceptionProcessor {

    @Override
    protected void process(Throwable t, MessageContext context) {
        super.process(t, context);
        if (t instanceof ClientWebApplicationException e) {
            context.setProperty(ExchangeProperty.ADDITIONAL_ERROR_DETAILS, e.getResponse().readEntity(String.class));
        }
    }
}
