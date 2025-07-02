package com.redhat.cloud.notifications.recipients.resolver.rbac;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class AuthRequestFilterTest {

    private static final String testToken = "{\"approval\":{\"secret\":\"123\"},\"advisor\":{\"alt-secret\":\"456\"},\"notifications\":{\"secret\":\"789\"}}";

    @BeforeEach
    void clean() {
        System.clearProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY);
        System.clearProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY);
    }

    @Test
    @DisplayName("Should contain approval and secret")
    void shouldContainApprovalAndSecret() {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "approval");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);
        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();

        assertEquals("approval", rbacAuthRequestFilter.getApplication());
        assertEquals("123", rbacAuthRequestFilter.getSecret());
    }

    @Test
    @DisplayName("Should not contain secret when service application property value is wrong")
    void shouldNotContainSecretWhenServiceApplicationValueIsWrong() {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "someWrongApplication");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);
        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();

        assertEquals("someWrongApplication", rbacAuthRequestFilter.getApplication());

        assertNull(rbacAuthRequestFilter.getSecret());
    }

    @Test
    @DisplayName("Should load alt-secret value if secret value is not set")
    void shouldLoadAltSecretIfSecretIsNotSet() {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "advisor");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);
        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();

        ClientRequestContext context = configureContext();

        rbacAuthRequestFilter.filter(context);

        assertNull(context.getHeaderString("Authorization"));

        assertEquals("456", context.getHeaderString("x-rh-rbac-psk"));
        assertEquals("advisor", context.getHeaderString("x-rh-rbac-client-id"));
    }

    @Test
    @DisplayName("Should not contain username and password in header when dev header is not set")
    void shouldNotContainUsernamePasswordHeaderWhenDevHeaderIsNotSet() {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "notifications");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);
        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();

        ClientRequestContext context = configureContext();

        rbacAuthRequestFilter.filter(context);

        assertNull(context.getHeaderString("Authorization"));

        assertEquals("789", context.getHeaderString("x-rh-rbac-psk"));
        assertEquals("notifications", context.getHeaderString("x-rh-rbac-client-id"));
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
