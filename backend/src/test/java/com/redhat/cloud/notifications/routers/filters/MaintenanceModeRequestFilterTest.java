package com.redhat.cloud.notifications.routers.filters;

import com.redhat.cloud.notifications.config.BackendConfig;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

@QuarkusTest
class MaintenanceModeRequestFilterTest {

    @Inject
    MaintenanceModeRequestFilter requestFilter;

    @InjectSpy
    BackendConfig backendConfig;

    @CacheName("maintenance")
    Cache maintenance;

    @ParameterizedTest
    @ValueSource(strings = {"/blabla", "/api/notifications/v1.0/notifications/eventTypes"})
    void shouldBeAffectedByMaintenanceMode(String requestPath) {
        assertTrue(requestFilter.isAffectedByMaintenanceMode(requestPath));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/metrics", "/internal", "/internal/admin/status", "/api/notifications/v1.0/status"})
    void shouldNotBeAffectedByMaintenanceModeWhenPathIsHealth(String requestPath) {
        assertFalse(requestFilter.isAffectedByMaintenanceMode(requestPath));
    }

    @Test
    void testAffectedByPartialMaintenanceMode() {
        ContainerRequestContext requestContext = buildContainerRequestContext("https://console.redhat.com/api/integrations/v1.0/endpoints/", "POST");
        assertNull(requestFilter.filter(requestContext));

        when(backendConfig.isMaintenanceModeEnabled(startsWith("POST_/api/integrations/v1.0/endpoints"))).thenReturn(true);
        maintenance.invalidateAll().await().indefinitely();

        requestContext = buildContainerRequestContext("https://console.redhat.com/api/integrations/v1.0/endpoints/", "POST");
        assertEquals(MaintenanceModeRequestFilter.MAINTENANCE_IN_PROGRESS, requestFilter.filter(requestContext));
        requestContext = buildContainerRequestContext("https://console.redhat.com/api/integrations/v1.0/endpoints/", "GET");
        assertNull(requestFilter.filter(requestContext));
    }

    ContainerRequestContext buildContainerRequestContext(final String requestPath, final String method) {
        return new ContainerRequestContext() {
            @Override
            public Object getProperty(String name) {
                return null;
            }

            @Override
            public Collection<String> getPropertyNames() {
                return List.of();
            }

            @Override
            public void setProperty(String name, Object object) {

            }

            @Override
            public void removeProperty(String name) {

            }

            @Override
            public UriInfo getUriInfo() {
                UriInfo uri = new UriInfo() {
                    @Override
                    public String getPath() {
                        return "";
                    }

                    @Override
                    public String getPath(boolean decode) {
                        return "";
                    }

                    @Override
                    public List<PathSegment> getPathSegments() {
                        return List.of();
                    }

                    @Override
                    public List<PathSegment> getPathSegments(boolean decode) {
                        return List.of();
                    }

                    @Override
                    public URI getRequestUri() {
                        URI uri = UriBuilder.fromUri(requestPath).build();
                        return uri;
                    }

                    @Override
                    public UriBuilder getRequestUriBuilder() {
                        return null;
                    }

                    @Override
                    public URI getAbsolutePath() {
                        return null;
                    }

                    @Override
                    public UriBuilder getAbsolutePathBuilder() {
                        return null;
                    }

                    @Override
                    public URI getBaseUri() {
                        return null;
                    }

                    @Override
                    public UriBuilder getBaseUriBuilder() {
                        return null;
                    }

                    @Override
                    public MultivaluedMap<String, String> getPathParameters() {
                        return null;
                    }

                    @Override
                    public MultivaluedMap<String, String> getPathParameters(boolean decode) {
                        return null;
                    }

                    @Override
                    public MultivaluedMap<String, String> getQueryParameters() {
                        return null;
                    }

                    @Override
                    public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
                        return null;
                    }

                    @Override
                    public List<String> getMatchedURIs() {
                        return List.of();
                    }

                    @Override
                    public List<String> getMatchedURIs(boolean decode) {
                        return List.of();
                    }

                    @Override
                    public List<Object> getMatchedResources() {
                        return List.of();
                    }

                    @Override
                    public URI resolve(URI uri) {
                        return null;
                    }

                    @Override
                    public URI relativize(URI uri) {
                        return null;
                    }
                };
                return uri;
            }

            @Override
            public void setRequestUri(URI requestUri) {

            }

            @Override
            public void setRequestUri(URI baseUri, URI requestUri) {

            }

            @Override
            public Request getRequest() {
                return null;
            }

            @Override
            public String getMethod() {
                return method;
            }

            @Override
            public void setMethod(String method) {

            }

            @Override
            public MultivaluedMap<String, String> getHeaders() {
                return null;
            }

            @Override
            public String getHeaderString(String name) {
                return "";
            }

            @Override
            public Date getDate() {
                return null;
            }

            @Override
            public Locale getLanguage() {
                return null;
            }

            @Override
            public int getLength() {
                return 0;
            }

            @Override
            public MediaType getMediaType() {
                return null;
            }

            @Override
            public List<MediaType> getAcceptableMediaTypes() {
                return List.of();
            }

            @Override
            public List<Locale> getAcceptableLanguages() {
                return List.of();
            }

            @Override
            public Map<String, Cookie> getCookies() {
                return Map.of();
            }

            @Override
            public boolean hasEntity() {
                return false;
            }

            @Override
            public InputStream getEntityStream() {
                return null;
            }

            @Override
            public void setEntityStream(InputStream input) {

            }

            @Override
            public SecurityContext getSecurityContext() {
                return null;
            }

            @Override
            public void setSecurityContext(SecurityContext context) {

            }

            @Override
            public void abortWith(Response response) {

            }
        };
    }
}
