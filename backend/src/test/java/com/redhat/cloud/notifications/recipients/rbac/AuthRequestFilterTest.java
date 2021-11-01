package com.redhat.cloud.notifications.recipients.rbac;

import io.quarkus.test.junit.QuarkusTest;
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

@QuarkusTest
public class AuthRequestFilterTest {

    private static final String testToken = "{\"approval\":{\"secret\":\"123\"},\"advisor\":{\"alt-secret\":\"456\"},\"notifications\":{\"secret\":\"789\"}}";

    @BeforeEach
    public void clean() {
        System.clearProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_DEV_EXCEPTIONAL_AUTH_KEY);
        System.clearProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY);
        System.clearProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY);
    }

    private AuthRequestFilter getAuthRequestFilter() {
        return new AuthRequestFilter();
    }

    @Test
    public void testLoadingFromConfiguration() {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "approval");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);
        AuthRequestFilter rbacAuthRequestFilter = getAuthRequestFilter();

        Assertions.assertEquals("approval", rbacAuthRequestFilter.getApplication());
        Assertions.assertEquals("123", rbacAuthRequestFilter.getSecret());
    }

    @Test
    public void testLoadingFromConfigurationWithWrongApplication() {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "idontknow");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);
        AuthRequestFilter rbacAuthRequestFilter = getAuthRequestFilter();

        Assertions.assertEquals("idontknow", rbacAuthRequestFilter.getApplication());
        Assertions.assertEquals(null, rbacAuthRequestFilter.getSecret());
    }

    @Test
    public void testServiceToServiceHeaders() throws IOException {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "notifications");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);
        AuthRequestFilter rbacAuthRequestFilter = getAuthRequestFilter();

        ClientRequestContext context = configureContext();

        rbacAuthRequestFilter.filter(context);
        MultivaluedMap<String, Object> map = context.getHeaders();
        Assertions.assertEquals("789", context.getHeaderString("x-rh-rbac-psk"));
        Assertions.assertEquals("notifications", context.getHeaderString("x-rh-rbac-client-id"));
        Assertions.assertNull(context.getHeaderString("Authorization"));
    }

    @Test
    public void testDevServiceToServiceHeaders() throws IOException {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "notifications");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_DEV_EXCEPTIONAL_AUTH_KEY, "myuser:p4ssw0rd");
        AuthRequestFilter rbacAuthRequestFilter = getAuthRequestFilter();

        ClientRequestContext context = configureContext();

        // Setting x-rh-rbac-account
        context.getHeaders().putSingle("x-rh-rbac-account", "the-account-id");

        rbacAuthRequestFilter.filter(context);
        Assertions.assertNull(context.getHeaderString("x-rh-rbac-psk"));
        Assertions.assertNull(context.getHeaderString("x-rh-rbac-client-id"));

        // Account is removed
        Assertions.assertNull(context.getHeaderString("x-rh-rbac-account"));
        Assertions.assertEquals(
                "Basic " + new String(Base64.getEncoder().encode("myuser:p4ssw0rd".getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8),
                context.getHeaderString("Authorization"));
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
