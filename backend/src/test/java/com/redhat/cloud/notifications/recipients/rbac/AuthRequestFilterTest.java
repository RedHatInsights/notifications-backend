package com.redhat.cloud.notifications.recipients.rbac;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class AuthRequestFilterTest {

    @BeforeEach
    public void clean() {
        System.setProperty("rbac.service-to-service.exceptional.auth.info", "");
    }

    @Test
    public void testServiceToServiceHeaders() throws IOException {
        ClientRequestContext context = configureContext();

        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();
        rbacAuthRequestFilter.application = "My nice app";
        rbacAuthRequestFilter.secret = "this-is-a-secret-token";

        rbacAuthRequestFilter.filter(context);
        MultivaluedMap<String, Object> map = context.getHeaders();
        Assertions.assertEquals("this-is-a-secret-token", context.getHeaderString("x-rh-rbac-psk"));
        Assertions.assertEquals("My nice app", context.getHeaderString("x-rh-rbac-client-id"));
        Assertions.assertNull(context.getHeaderString("Authorization"));
    }

    @Test
    public void testDevServiceToServiceHeaders() throws IOException {
        System.setProperty("rbac.service-to-service.exceptional.auth.info", "myuser:p4ssw0rd");
        ClientRequestContext context = configureContext();

        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();
        rbacAuthRequestFilter.application = "My nice app";
        rbacAuthRequestFilter.secret = "this-is-a-secret-token";

        // Setting x-rh-rbac-account
        context.getHeaders().putSingle("x-rh-rbac-account", "the-account-id");

        rbacAuthRequestFilter.filter(context);
        Assertions.assertNull(context.getHeaderString("x-rh-rbac-psk"));
        Assertions.assertNull(context.getHeaderString("x-rh-rbac-client-id"));

        // Account is removed
        Assertions.assertNull(context.getHeaderString("x-rh-rbac-account"));
        Assertions.assertEquals(
                "Basic " + new String(Base64.getEncoder().encode("myuser:p4ssw0rd".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8),
                context.getHeaderString("Authorization")
        );
    }

    private ClientRequestContext configureContext() {
        ClientRequestContext context = Mockito.mock(ClientRequestContext.class);
        MultivaluedMap<String, Object> map = new MultivaluedHashMap<>();
        Mockito.when(context.getHeaders()).thenReturn(map);
        Mockito.when(context.getHeaderString(Mockito.anyString())).then(invocationOnMock -> {
            Object o = map.get(invocationOnMock.getArgument(0));
            if (o instanceof List) {
                return ((List) o).get(0);
            }

            return o;
        });
        return context;
    }
}
