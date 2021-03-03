package com.redhat.cloud.notifications.rbac;

import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocationBuilder;
import org.jboss.resteasy.client.jaxrs.internal.ClientRequestContextImpl;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.ClientRequestContext;
import java.io.IOException;
import java.util.Base64;

public class RbacServiceToServiceAuthRequestFilterTest {

    @Test
    void testServiceToServiceHeaders() {
        RbacServiceToServiceAuthRequestFilter requestFilter = new RbacServiceToServiceAuthRequestFilter();
        requestFilter.application = "My nice app";
        requestFilter.secret = "this-is-a-secret-token";

        ClientConfiguration configuration = new ClientConfiguration(ResteasyProviderFactory.getInstance());
        ClientInvocation invocation =  (ClientInvocation) new ClientInvocationBuilder(null, null, configuration).buildGet();
        ClientRequestContext context = new ClientRequestContextImpl(invocation);
        try {
            requestFilter.filter(context);
            Assertions.assertEquals("this-is-a-secret-token", context.getHeaderString("x-rh-rbac-psk"));
            Assertions.assertEquals("My nice app", context.getHeaderString("x-rh-rbac-client-id"));
            Assertions.assertNull(context.getHeaderString("Authorization"));
        } catch (IOException ex) {
            Assertions.fail(ex);
        }
    }

    @Test
    void testDevServiceToServiceHeaders() {
        System.setProperty("rbac.service-to-service.exceptional.auth.info", "myuser:p4ssw0rd");
        RbacServiceToServiceAuthRequestFilter requestFilter = new RbacServiceToServiceAuthRequestFilter();
        requestFilter.application = "My nice app";
        requestFilter.secret = "this-is-a-secret-token";

        ClientConfiguration configuration = new ClientConfiguration(ResteasyProviderFactory.getInstance());
        ClientInvocation invocation =  (ClientInvocation) new ClientInvocationBuilder(null, null, configuration).buildGet();
        ClientRequestContext context = new ClientRequestContextImpl(invocation);

        context.getHeaders().putSingle("x-rh-rbac-account", "the-account-id");

        try {
            requestFilter.filter(context);
            Assertions.assertNull(context.getHeaderString("x-rh-rbac-psk"));
            Assertions.assertNull(context.getHeaderString("x-rh-rbac-client-id"));
            Assertions.assertNull(context.getHeaderString("x-rh-rbac-account"));
            Assertions.assertEquals(
                    "Basic " + Base64.getEncoder().encodeToString("myuser:p4ssw0rd".getBytes()),
                    context.getHeaderString("Authorization")
            );
        } catch (IOException ex) {
            Assertions.fail(ex);
        } finally {
            System.setProperty("rbac.service-to-service.exceptional.auth.info", "");
        }
    }
}
