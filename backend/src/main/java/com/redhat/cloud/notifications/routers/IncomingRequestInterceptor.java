package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.principal.ConsoleIdentity;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import io.quarkus.logging.Log;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

// test comment

@Provider
@PreMatching
public class IncomingRequestInterceptor implements ContainerRequestFilter {


    // Prevents the injection of characters that would break the log file pattern and lead to log forging or log poisoning.
    private static final Pattern ANTI_INJECTION_PATTERN = Pattern.compile("[\n|\r|\t]");

    private static final Pattern p = Pattern.compile("/api/(integrations|notifications)/v(\\d+)/(.*)");

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        openapiAcceptHeaderMangler(requestContext);

        routeRedirector(requestContext);

        addOrgIdToMDCForAccessLogs(requestContext);
    }

    /**
     * Change the accept header if needed for Openapi requests when
     * openapi.json is requested.
     */
    private static void openapiAcceptHeaderMangler(ContainerRequestContext requestContext) {
        /*
         * CPOL-107
         * Default return format for openapi is .yml
         * If the user requests 'openapi.json', the user assumes
         * that a JSON format is returned. Unfortunately does Quarkus not
         * honor the '.json' suffix but either requires a correct Accept
         * header or the use of a query parameter.
         *
         * We now look at the path and if it ends in .json, replace the
         * existing Accept header with one that requests Json format.
         */

        if (requestContext.getUriInfo().getPath().endsWith("openapi.json")) {
            requestContext.getHeaders().remove("Accept");
            requestContext.getHeaders().add("Accept", "application/json");
        }
    }

    /**
     * Add org id to Mapped diagnostic context to be accessible from accessLogs
     */
    private static void addOrgIdToMDCForAccessLogs(ContainerRequestContext requestContext) {
        if (requestContext.getHeaders().containsKey(X_RH_IDENTITY_HEADER)) {
            ConsoleIdentity identity = ConsoleIdentityProvider.getRhIdentityFromString(requestContext.getHeaders().getFirst(X_RH_IDENTITY_HEADER));
            if (identity instanceof RhIdentity rhIdentity) {
                MDC.put("x-rh-org-id", rhIdentity.getOrgId());
            }
        }
    }

    /**
     * If the requested route is the one with major version only,
     * we rewrite it on the fly.
     */
    private void routeRedirector(ContainerRequestContext requestContext) {

        String uri = requestContext.getUriInfo().getPath();
        if (Log.isTraceEnabled()) {
            String sanitizedUri = ANTI_INJECTION_PATTERN.matcher(uri).replaceAll("");
            Log.tracef("Incoming uri: %s", sanitizedUri);
        }
        Matcher m = p.matcher(uri);
        if (m.matches()) {
            String newTarget = "/api/" + m.group(1) + "/v" + m.group(2) + ".0/" + m.group(3);
            Log.tracef("Rerouting to: %s", newTarget);

            requestContext.setRequestUri(UriBuilder.fromUri(requestContext.getUriInfo().getRequestUri()).replacePath(newTarget).build());
        }
    }
}
