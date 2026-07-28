package com.redhat.cloud.notifications.routers;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncomingRequestInterceptorTest {

    private final IncomingRequestInterceptor interceptor = new IncomingRequestInterceptor();

    @ParameterizedTest
    @CsvSource({
        "GET,/api/notifications/v2.0/user-config/subscriptions",
        "PUT,/api/notifications/v2.0/user-config/subscriptions"
    })
    void shouldNotRewriteUserConfigSubscriptionsRegardlessOfMethod(String method, String path) throws IOException {
        assertNoRewrite(method, path);
    }

    @Test
    void shouldRewritePutToAnotherNotificationsV2PathToV1() throws IOException {
        // Only user-config/subscriptions is exempt on non-GET methods; any other v2 notifications
        // path is still expected to fall back to v1 the way it did before this branch's changes.
        assertRewrite("PUT", "/api/notifications/v2.0/some-other-endpoint", "/api/notifications/v1.0/some-other-endpoint");
    }

    @Test
    void shouldNotRewriteNotificationsBehaviorGroupsGetToV1() throws IOException {
        assertNoRewrite("GET", "/api/notifications/v2.0/notifications/eventTypes/abc/behaviorGroups");
    }

    @Test
    void shouldRewritePostToNotificationsBehaviorGroupsPathToV1() throws IOException {
        // The behaviorGroups GET-only exemption must still not apply to other methods.
        assertRewrite("POST", "/api/notifications/v2.0/notifications/eventTypes/abc/behaviorGroups",
            "/api/notifications/v1.0/notifications/eventTypes/abc/behaviorGroups");
    }

    @Test
    void shouldNotRewriteIntegrationsEndpointsGetToV1() throws IOException {
        assertNoRewrite("GET", "/api/integrations/v2.0/endpoints");
    }

    @Test
    void shouldRewriteIntegrationsEndpointsPutToV1() throws IOException {
        // The integrations/endpoints GET-only exemption must still not apply to other methods.
        assertRewrite("PUT", "/api/integrations/v2.0/endpoints", "/api/integrations/v1.0/endpoints");
    }

    @Test
    void shouldNotRewriteOpenApiJsonGetToV1() throws IOException {
        assertNoRewrite("GET", "/api/notifications/v2.0/openapi.json");
    }

    private void assertNoRewrite(String method, String path) throws IOException {
        ContainerRequestContext requestContext = mockRequestContext(method, path);
        interceptor.filter(requestContext);
        verify(requestContext, never()).setRequestUri(any(URI.class));
    }

    private void assertRewrite(String method, String path, String expectedNewPath) throws IOException {
        ContainerRequestContext requestContext = mockRequestContext(method, path);
        interceptor.filter(requestContext);
        var captor = forClass(URI.class);
        verify(requestContext).setRequestUri(captor.capture());
        assertEquals(expectedNewPath, captor.getValue().getPath());
    }

    private ContainerRequestContext mockRequestContext(String method, String path) {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn(path);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://console.redhat.com" + path));
        when(requestContext.getMethod()).thenReturn(method);
        when(requestContext.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        return requestContext;
    }
}
