package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.Base64Utils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class AuthRequestFilterTest {

    private static final String testToken = "{\"approval\":{\"secret\":\"123\"},\"advisor\":{\"alt-secret\":\"456\"},\"notifications\":{\"secret\":\"789\"}}";

    @BeforeEach
    void clean() {
        System.clearProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_DEV_EXCEPTIONAL_AUTH_KEY);
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
    @DisplayName("Should not contain username and password in header when dev header is not set")
    void shouldNotContainUsernamePasswordHeaderWhenDevHeaderIsNotSet() throws IOException {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "notifications");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);
        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();

        ClientRequestContext context = configureContext();

        rbacAuthRequestFilter.filter(context);

        assertNull(context.getHeaderString("Authorization"));

        assertEquals("789", context.getHeaderString("x-rh-rbac-psk"));
        assertEquals("notifications", context.getHeaderString("x-rh-rbac-client-id"));
    }

    @Test
    @DisplayName("Should not contain any headers besides username password when exceptional dev header is set")
    void shouldContainAuthorizationHeaderOnlyWhenDevHeaderIsSet() throws IOException {
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, "notifications");
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, testToken);

        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_DEV_EXCEPTIONAL_AUTH_KEY, "myuser:p4ssw0rd");

        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();

        ClientRequestContext context = configureContext();

        // Setting x-rh-rbac-account
        context.getHeaders().putSingle("x-rh-rbac-account", "the-account-id");
        context.getHeaders().putSingle("x-rh-rbac-org-id", "the-org-id");

        rbacAuthRequestFilter.filter(context);
        assertNull(context.getHeaderString("x-rh-rbac-psk"));
        assertNull(context.getHeaderString("x-rh-rbac-client-id"));

        // Account is removed
        assertNull(context.getHeaderString("x-rh-rbac-account"));
        assertNull(context.getHeaderString("x-rh-rbac-org-id"));

        assertEquals(
                "Basic " + Base64Utils.encode("myuser:p4ssw0rd"),
                context.getHeaderString("Authorization")
        );
    }

    @Test
    void shouldremoveAccoundIdAndOrgIdFromHeaderWhenAuthInfoIsPresent() throws IOException {
        // given
        System.setProperty(AuthRequestFilter.RBAC_SERVICE_TO_SERVICE_DEV_EXCEPTIONAL_AUTH_KEY, "myuser:p4ssw0rd");
        ClientRequestContext context = configureContext();
        AuthRequestFilter rbacAuthRequestFilter = new AuthRequestFilter();

        context.getHeaders().putSingle("x-rh-rbac-account", "the-account-id");
        context.getHeaders().putSingle("x-rh-rbac-org-id", "the-org-id");

        // when
        rbacAuthRequestFilter.filter(context);

        // then
        assertNull(context.getHeaderString("x-rh-rbac-account"));
        assertNull(context.getHeaderString("x-rh-rbac-org-id"));
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
