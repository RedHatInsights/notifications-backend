package com.redhat.cloud.notifications.auth.annotation;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.routers.SecurityContextUtil;
import com.redhat.cloud.notifications.security.SecurityLog;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.SecurityContext;

import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.UUID;

/**
 * The interceptor's implementation for the {@link Authorization} annotaiton.
 * On any method annotated with the aforementioned annotation, it will perform
 * authorization checks depending on whether RBAC or Kessel authorization back
 * ends are enabled.
 */
@Interceptor
@Authorization(legacyRBACRole = "")
@Priority(Interceptor.Priority.APPLICATION)
public class AuthorizationInterceptor {

    private final BackendConfig backendConfig;
    private final WorkspaceUtils workspaceUtils;
    private final KesselInventoryAuthorization kesselInventoryAuthorization;

    /**
     * Constructor for the interceptor. Helps with testing, as otherwise the
     * interceptor cannot get its dependencies injected.
     * @param backendConfig the back end's configuration bean.
     * @param kesselInventoryAuthorization the Kessel's inventory authorization bean.
     * @param workspaceUtils the workspace utils' bean.
     */
    public AuthorizationInterceptor(
        final BackendConfig backendConfig,
        final WorkspaceUtils workspaceUtils,
        final KesselInventoryAuthorization kesselInventoryAuthorization
    ) {
        this.backendConfig = backendConfig;
        this.workspaceUtils = workspaceUtils;
        this.kesselInventoryAuthorization = kesselInventoryAuthorization;
    }

    @AroundInvoke
    public Object aroundInvoke(final InvocationContext ctx) throws Exception {
        // Grab the annotation from the method. The annotation will never be
        // null, because the interceptor only works with those methods that
        // have been annotated.
        final Authorization annotation = ctx.getMethod().getDeclaredAnnotation(Authorization.class);

        // Get the parameter indexes from the method's definition. By having
        // these indexes, we can simply then grab the exact intercepted
        // parameters from the array of intercepted method parameters.
        final ParameterIndexes parameterIndexes = this.getParameterIndexes(ctx);

        // When the method doesn't have a "SecurityContext" parameter we cannot
        // perform the authorization checks, and therefore we cannot continue.
        if (parameterIndexes.getSecurityContextIndex().isEmpty()) {
            throw new IllegalStateException(String.format("The security context is not set on the method \"%s\", which is needed for the \"KesselRequiredPermission\" annotation to work", ctx.getMethod().getName()));
        }

        // Grab the intercepted parameters and make sure that the security
        // context is present, as otherwise we cannot perform any authorization
        // checks.
        final Object[] interceptedParameters = ctx.getParameters();

        // Since we grabbed the parameter index for the "SecurityContext"
        // parameter, we can safely cast that parameter to that so that we can
        // use it.
        final SecurityContext securityContext = (SecurityContext) interceptedParameters[parameterIndexes.getSecurityContextIndex().get()];

        // Perform the legacy RBAC check first. The only reason is to be able
        // to return early and make the code easier to follow.
        if (!this.backendConfig.isKesselEnabled(SecurityContextUtil.getOrgId(securityContext))) {
            // Legacy RBAC permission checking. The permission will have been
            // prefetched and processed by the "ConsoleIdentityProvider".
            if (securityContext.isUserInRole(annotation.legacyRBACRole())) {
                return ctx.proceed();
            } else {
                SecurityLog.logAuthzFailure(annotation.resourceType(), annotation.legacyRBACRole(), securityContext);
                throw new ForbiddenException();
            }
        }

        // When the execution reaches this point we can be sure that the
        // enabled authorization back end is "Kessel", so we proceed to get the
        // Kessel permissions.
        final WorkspacePermission[] workspacePermissions = annotation.workspacePermissions();

        // Make sure that we have workspace permissions to check.
        // If we don't, that probably signals a mistake on the developer's side.
        if (workspacePermissions.length == 0) {
            throw new IllegalStateException(String.format("No workspace permissions were set for method \"%s\", and at least one of them is required for the \"KesselRequiredPermission\" annotation to work", ctx.getMethod().getName()));
        }

        // Check the workspace permissions.
        if (workspacePermissions.length > 0) {
            final UUID workspaceId = resolveWorkspaceId(parameterIndexes, interceptedParameters, securityContext);
            for (final WorkspacePermission workspacePermission : workspacePermissions) {
                this.kesselInventoryAuthorization.hasPermissionOnWorkspace(securityContext, workspacePermission, workspaceId);
            }
        }

        return ctx.proceed();
    }

    /**
     * Resolves the workspace to perform the Kessel permission check against.
     * When the caller supplied an {@code x-rh-workspace-id} header (via a
     * method parameter annotated with {@code @HeaderParam}), that workspace
     * is used directly -- the Kessel check right after this call is what
     * actually validates it, since an unknown or unauthorized workspace ID
     * simply comes back as "not allowed". Otherwise, falls back to the
     * org's default workspace, same as before this header existed.
     * @param parameterIndexes the resolved parameter indexes for the
     *                         intercepted method.
     * @param interceptedParameters the intercepted method's parameters.
     * @param securityContext the security context of the request.
     * @return the workspace identifier to check permissions against.
     */
    private UUID resolveWorkspaceId(final ParameterIndexes parameterIndexes, final Object[] interceptedParameters, final SecurityContext securityContext) {
        final Optional<Integer> workspaceIdHeaderIndex = parameterIndexes.getWorkspaceIdHeaderIndex();
        if (workspaceIdHeaderIndex.isPresent()) {
            final String workspaceIdHeader = (String) interceptedParameters[workspaceIdHeaderIndex.get()];
            if (workspaceIdHeader != null && !workspaceIdHeader.isBlank()) {
                try {
                    return UUID.fromString(workspaceIdHeader);
                } catch (final IllegalArgumentException e) {
                    SecurityLog.logAuthzFailure("workspace", "malformed " + Constants.X_RH_WORKSPACE_ID_HEADER + " header", securityContext);
                    throw new ForbiddenException();
                }
            }
        }

        return workspaceUtils.getDefaultWorkspaceId(SecurityContextUtil.getOrgId(securityContext));
    }

    /**
     * Grabs the indexes for the parameters we are interested in.
     * @param ctx the method's invocation context.
     * @return a {@link ParameterIndexes} object containing the relevant
     * indexes we are interested in.
     */
    private ParameterIndexes getParameterIndexes(final InvocationContext ctx) {
        final ParameterIndexes parameterIndexes = new ParameterIndexes();

        final Parameter[] methodParameters = ctx.getMethod().getParameters();
        for (int i = 0; i < methodParameters.length; i++) {
            final Parameter parameter = methodParameters[i];

            if (parameter.getType().equals(SecurityContext.class)) {
                parameterIndexes.setSecurityContextIndex(i);
            }

            final HeaderParam headerParam = parameter.getAnnotation(HeaderParam.class);
            if (headerParam != null && Constants.X_RH_WORKSPACE_ID_HEADER.equals(headerParam.value())) {
                parameterIndexes.setWorkspaceIdHeaderIndex(i);
            }
        }
        return parameterIndexes;
    }
}
