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

@Provider
@PreMatching
public class IncomingRequestInterceptor implements ContainerRequestFilter {


    // Prevents the injection of characters that would break the log file pattern and lead to log forging or log poisoning.
    private static final Pattern ANTI_INJECTION_PATTERN = Pattern.compile("[\n|\r|\t]");

    private static final Pattern p = Pattern.compile("/api/(integrations|notifications)/v(\\d+)/(.*)");
    private static final Pattern patternV2 = Pattern.compile("/api/(integrations|notifications)/v2.0/(.*)");
    private static final Pattern patternNotificationsBgV2 = Pattern.compile("notifications/eventTypes/(.*)/behaviorGroups");
    private static final Pattern patternIntegrationEndpointsV2 = Pattern.compile("endpoints(.*)");
    private static final Pattern patternIntegrationEndpointsDetailsV2 = Pattern.compile("endpoints/(.*)/details");
    private static final Pattern patternOpenApi = Pattern.compile("openapi.json");
    // RHCLOUD-49569: user-config/subscriptions is v2-only (no v1 equivalent to fall back to), and
    // unlike the other v2-only paths below it also has a PUT, not just a GET. See the comment in
    // routeRedirector() for why that requires its own branch instead of reusing the GET-only ones.
    private static final Pattern patternUserConfigSubscriptionsV2 = Pattern.compile("user-config/subscriptions");

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
            uri = requestContext.getUriInfo().getPath();
        }

        // Most v2.0 paths are just an alias of the same v1.0 endpoint (same behavior, versioned
        // path only), so by default any request to a v2.0 path gets silently rewritten to v1.0
        // below. A path only needs an entry here once it has *real* v2-only behavior of its own
        // (a different response shape, or an endpoint with no v1 equivalent at all) that must NOT
        // be rewritten. Historically only GET endpoints needed this (see the "GET".equals(...)
        // checks below for notifications/integrations), so the rewriteToV1 = false exemptions were
        // only ever checked for GET requests. user-config/subscriptions is v2-only for PUT too
        // (RHCLOUD-49569), so it's checked unconditionally, before the method is even looked at -
        // without that, its PUT would still match patternV2 (patterns match any method) and would
        // get rewritten to a v1.0 path that was never implemented, silently 404-ing.
        Matcher matcherUrlV2 = patternV2.matcher(uri);
        if (matcherUrlV2.matches()) {
            boolean rewriteToV1 = true;
            if (matcherUrlV2.group(1).equals("notifications")) {
                Matcher userConfigSubscriptions = patternUserConfigSubscriptionsV2.matcher(matcherUrlV2.group(2));
                if (userConfigSubscriptions.matches()) {
                    rewriteToV1 = false;
                } else if ("GET".equals(requestContext.getMethod())) {
                    Matcher notificationsBG = patternNotificationsBgV2.matcher(matcherUrlV2.group(2));
                    Matcher openApi = patternOpenApi.matcher(matcherUrlV2.group(2));
                    if (notificationsBG.matches() || openApi.matches()) {
                        rewriteToV1 = false;
                    }
                }
            } else if ("GET".equals(requestContext.getMethod()) && matcherUrlV2.group(1).equals("integrations")) {
                Matcher integrationsEndpoints = patternIntegrationEndpointsV2.matcher(matcherUrlV2.group(2));
                Matcher integrationsEndpointsHistoryDetails = patternIntegrationEndpointsDetailsV2.matcher(matcherUrlV2.group(2));
                Matcher openApi = patternOpenApi.matcher(matcherUrlV2.group(2));
                if ((integrationsEndpoints.matches() && !integrationsEndpointsHistoryDetails.matches())
                    || openApi.matches()) {
                    rewriteToV1 = false;
                }
            }

            if (rewriteToV1) {
                String newTarget = "/api/" + matcherUrlV2.group(1) + "/v1.0/" + matcherUrlV2.group(2);
                requestContext.setRequestUri(UriBuilder.fromUri(requestContext.getUriInfo().getRequestUri()).replacePath(newTarget).build());
            }
        }
    }
}
