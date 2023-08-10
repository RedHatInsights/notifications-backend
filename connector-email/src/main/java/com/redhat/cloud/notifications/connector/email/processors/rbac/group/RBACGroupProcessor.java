package com.redhat.cloud.notifications.connector.email.processors.rbac.group;

import com.redhat.cloud.notifications.connector.email.processors.rbac.RBACConstants;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RBACGroupProcessor implements Processor {
    @Override
    public void process(final Exchange exchange) {
        final String body = exchange.getMessage().getBody(String.class);
        final JsonObject rbacGroup = new JsonObject(body);

        exchange.setProperty(RBACConstants.RBAC_GROUP_IS_PLATFORM_DEFAULT, rbacGroup.getBoolean("platform_default"));
    }
}
