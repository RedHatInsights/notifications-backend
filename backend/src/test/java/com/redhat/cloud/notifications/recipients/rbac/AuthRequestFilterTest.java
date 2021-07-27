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

    private static final String testToken = "{\"approval\":{\"secret\":\"123\"},\"advisor\":{\"secret\":\"456\"},\"notifications\":{\"secret\":\"789\"}}";

    @BeforeEach
    public void clean() {
        System.clearProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_DEV_EXCEPTIONAL_AUTH_KEY);
        System.clearProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY);
        System.clearProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY);
    }

    @Test
    public void testLoadingFromConfiguration() throws IOException {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "approval");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);

        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();
        Assertions.assertEquals("approval", rbacAuthRequestFilter.application);
        Assertions.assertEquals("123", rbacAuthRequestFilter.secret);
    }

    @Test
    public void testLoadingFromConfigurationWithWrongApplication() throws IOException {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "idontknow");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);

        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();
        Assertions.assertEquals("idontknow", rbacAuthRequestFilter.application);
        Assertions.assertEquals(null, rbacAuthRequestFilter.secret);
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
