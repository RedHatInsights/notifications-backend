package com.redhat.cloud.notifications.auth.annotation;

import com.redhat.cloud.notifications.auth.kessel.KesselCheckClient;
import com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization;
import com.redhat.cloud.notifications.auth.kessel.KesselTestHelper;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.project_kessel.api.inventory.v1beta2.Allowed;

import java.lang.reflect.Method;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_VIEW;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.project_kessel.api.inventory.v1beta2.Allowed.ALLOWED_FALSE;
import static org.project_kessel.api.inventory.v1beta2.Allowed.ALLOWED_TRUE;

@QuarkusTest
public class AuthorizationInterceptorTest {
    @Inject
    KesselTestHelper kesselTestHelper;

    @Inject
    KesselInventoryAuthorization kesselInventoryAuthorization;

    @InjectMock
    BackendConfig backendConfig;

    @InjectMock
    KesselCheckClient kesselCheckClient;

    @InjectMock
    WorkspaceUtils workspaceUtils;

    public static final String LEGACY_RBAC_ROLE = "legacy-rbac-role";

    private AuthorizationInterceptor authorizationInterceptor;

    /**
     * Mocks the security context with a {@link RhIdentity} as the principal.
     * @return the mocked security context.
     */
    private SecurityContext mockSecurityContext() {
        return this.mockSecurityContext(null);
    }

    /**
     * Mocks the security context with a {@link RhIdentity} as the principal.
     * @param authorizedRbacRole the RBAC role the principal will be authorized
     *                           for. When the parameter is {@code null}, then
     *                           the principal will not be authorized.
     * @return the mocked security context.
     */
    private SecurityContext mockSecurityContext(final String authorizedRbacRole) {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Authorize the user on the given RBAC role, if any.
        Mockito.when(mockedSecurityContext.isUserInRole(authorizedRbacRole)).thenReturn(authorizedRbacRole != null && !authorizedRbacRole.isBlank());

        // Create a RhIdentity principal and assign it to the mocked security
        // context.
        final RhIdentity identity = Mockito.mock(RhIdentity.class);
        Mockito.when(identity.getName()).thenReturn("Red Hat user");
        Mockito.when(identity.getOrgId()).thenReturn(DEFAULT_ORG_ID);
        Mockito.when(identity.getUserId()).thenReturn(DEFAULT_USER);

        final ConsolePrincipal<?> principal = new RhIdPrincipal(identity);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(principal);

        return mockedSecurityContext;
    }

    /**
     * Manually set up the authorization interceptor, as using CDI injections
     * does not work.
     */
    @BeforeEach
    public void setUp() {
        this.authorizationInterceptor = new AuthorizationInterceptor(this.backendConfig, this.workspaceUtils, this.kesselInventoryAuthorization);
        when(workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)).thenReturn(KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID);
    }

    /**
     * Tests that when the method does not have a {@link jakarta.ws.rs.core.SecurityContext}
     * parameter, then the function under test throws an exception.
     *
     * @throws NoSuchMethodException when the method's specification is not
     * properly defined, and therefore we cannot get it to perform the tests.
     */
    @Test
    void testMissingSecurityContextThrowsException() throws NoSuchMethodException {
        // Mock the invocation context and make it return one of our defined
        // methods in this class, for simplicity.
        final InvocationContext invocationContext = Mockito.mock(InvocationContext.class);

        final Method methodUnderTest = AuthorizationInterceptorHelper.class.getMethod("testMethodWithoutSecurityContext", String.class, UUID.class);
        Mockito.when(invocationContext.getMethod()).thenReturn(methodUnderTest);

        // Call the function under test which should throw an exception that
        // signals that the security context is required in the annotated
        // method's signature.
        final IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.authorizationInterceptor.aroundInvoke(invocationContext)
        );

        // Assert that the expected exception was thrown.
        Assertions.assertEquals(
            String.format("The security context is not set on the method \"%s\", which is needed for the \"KesselRequiredPermission\" annotation to work", methodUnderTest.getName()),
            exception.getMessage()
        );
    }

    /**
     * Tests that the function under test does not throw an exception when the
     * principal has the expected legacy RBAC role.
     *
     * @throws Exception when an unexpected error occurs.
     * @throws NoSuchMethodException when the method's specification is not
     * properly defined, and therefore we cannot get it to perform the tests.
     */
    @Test
    void testRBACPermissionGranted() throws Exception, NoSuchMethodException {
        // Mock the invocation context and make it return one of our defined
        // methods in this class, for simplicity.
        final InvocationContext invocationContext = Mockito.mock(InvocationContext.class);

        final Method methodUnderTest = AuthorizationInterceptorHelper.class.getMethod("testMethodWithRBACRole", SecurityContext.class, String.class, UUID.class);
        Mockito.when(invocationContext.getMethod()).thenReturn(methodUnderTest);

        // Mock the returned parameters by the context, to be able to provide
        // whichever security context we want to the interceptor.
        final SecurityContext securityContext = this.mockSecurityContext(LEGACY_RBAC_ROLE);
        Mockito.when(invocationContext.getParameters()).thenReturn(new Object[]{securityContext});

        // Call the function under test which should not throw any exceptions.
        this.authorizationInterceptor.aroundInvoke(invocationContext);
    }

    /**
     * Tests that the function under test throws a {@link ForbiddenException}
     * when the principal does hot have the expected legacy RBAC role.
     *
     * @throws NoSuchMethodException when the method's specification is not
     * properly defined, and therefore we cannot get it to perform the tests.
     */
    @Test
    void testRBACPermissionDenied() throws NoSuchMethodException {
        // Mock the invocation context and make it return one of our defined
        // methods in this class, for simplicity.
        final InvocationContext invocationContext = Mockito.mock(InvocationContext.class);

        final Method methodUnderTest = AuthorizationInterceptorHelper.class.getMethod("testMethodWithRBACRole", SecurityContext.class, String.class, UUID.class);
        Mockito.when(invocationContext.getMethod()).thenReturn(methodUnderTest);

        // Make sure that Kessel is disabled for this test.
        when(backendConfig.isKesselEnabled(anyString())).thenReturn(false);

        // Mock the returned parameters by the context, to be able to provide
        // whichever security context we want to the interceptor.
        final SecurityContext securityContext = this.mockSecurityContext();
        Mockito.when(invocationContext.getParameters()).thenReturn(new Object[]{securityContext});

        // Call the function under test which should throw a ForbiddenException.
        Assertions.assertThrows(
            ForbiddenException.class,
            () -> this.authorizationInterceptor.aroundInvoke(invocationContext)
        );
    }

    /**
     * Tests that the function under test throws an exception when Kessel is
     * enabled as the authorization back end, and no {@link WorkspacePermission}
     * elements were defined in the annotation.
     *
     * @throws NoSuchMethodException when the method's specification is not
     * properly defined, and therefore we cannot get it to perform the tests.
     */
    @Test
    void testMissingWorkspacePermissions() throws NoSuchMethodException {
        // Mock the invocation context and make it return one of our defined
        // methods in this class, for simplicity.
        final InvocationContext invocationContext = Mockito.mock(InvocationContext.class);

        final Method methodUnderTest = AuthorizationInterceptorHelper.class.getMethod("testMethodWithRBACRole", SecurityContext.class, String.class, UUID.class);
        Mockito.when(invocationContext.getMethod()).thenReturn(methodUnderTest);

        // Make sure that Kessel is enabled for this test.
        when(backendConfig.isKesselEnabled(anyString())).thenReturn(true);

        // Mock the returned parameters by the context, to be able to provide
        // whichever security context we want to the interceptor.
        final SecurityContext securityContext = this.mockSecurityContext();
        Mockito.when(invocationContext.getParameters()).thenReturn(new Object[]{securityContext});

        // Call the function under test which should throw an exception that
        // signals that the required permissions are not defined in the
        // annotation.
        final IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.authorizationInterceptor.aroundInvoke(invocationContext)
        );

        // Assert that the expected exception was thrown.
        Assertions.assertEquals(
            String.format("No workspace permissions were set for method \"%s\", and at least one of them is required for the \"KesselRequiredPermission\" annotation to work", methodUnderTest.getName()),
            exception.getMessage()
        );
    }

    /**
     * Tests that the function under test does not throw an exception and runs
     * normally when the principal is authorized for the specified workspace
     * permissions in the annotation.
     *
     * @throws Exception when an unexpected error occurs.
     * @throws NoSuchMethodException when the method's specification is not
     * properly defined, and therefore we cannot get it to perform the tests.
     */
    @Test
    void testWorkspacePermissionGranted() throws Exception, NoSuchMethodException {
        // Mock the invocation context and make it return one of our defined
        // methods in this class, for simplicity.
        final InvocationContext invocationContext = Mockito.mock(InvocationContext.class);

        final Method methodUnderTest = AuthorizationInterceptorHelper.class.getMethod("testMethodWithWorkspacePermissions", SecurityContext.class, String.class, UUID.class);
        Mockito.when(invocationContext.getMethod()).thenReturn(methodUnderTest);

        // Make sure that Kessel is enabled for this test.
        when(backendConfig.isKesselEnabled(anyString())).thenReturn(true);

        // Mock the returned parameters by the context, to be able to provide
        // whichever security context we want to the interceptor.
        final SecurityContext securityContext = this.mockSecurityContext();
        Mockito.when(invocationContext.getParameters()).thenReturn(new Object[]{securityContext});

        // Mock the Kessel checks to simulate that the principal has the
        // required workspace permissions.
        when(workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)).thenReturn(KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID);
        mockKesselPermission(NOTIFICATIONS_VIEW, ALLOWED_TRUE);

        // Call the function under test which should not throw any exceptions.
        this.authorizationInterceptor.aroundInvoke(invocationContext);
    }

    /**
     * Tests that the function under test does not throw an exception and runs
     * normally when the principal is authorized for the specified workspace
     * permissions in the annotation.
     *
     * @throws Exception when an unexpected error occurs.
     * @throws NoSuchMethodException when the method's specification is not
     * properly defined, and therefore we cannot get it to perform the tests.
     */
    @Test
    void testWorkspacePermissionDenied() throws Exception, NoSuchMethodException {
        // Mock the invocation context and make it return one of our defined
        // methods in this class, for simplicity.
        final InvocationContext invocationContext = Mockito.mock(InvocationContext.class);

        final Method methodUnderTest = AuthorizationInterceptorHelper.class.getMethod("testMethodWithWorkspacePermissions", SecurityContext.class, String.class, UUID.class);
        Mockito.when(invocationContext.getMethod()).thenReturn(methodUnderTest);

        // Make sure that Kessel is enabled for this test.
        when(backendConfig.isKesselEnabled(anyString())).thenReturn(true);

        // Mock the returned parameters by the context, to be able to provide
        // whichever security context we want to the interceptor.
        final SecurityContext securityContext = this.mockSecurityContext();
        Mockito.when(invocationContext.getParameters()).thenReturn(new Object[]{securityContext});

        // Mock the Kessel checks to simulate that the principal has the
        // required workspace permissions.
        when(workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)).thenReturn(KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID);
        mockKesselPermission(NOTIFICATIONS_VIEW, ALLOWED_FALSE);

        // Call the function under test which should throw a ForbiddenException.
        Assertions.assertThrows(
            ForbiddenException.class,
            () -> this.authorizationInterceptor.aroundInvoke(invocationContext)
        );
    }

    private void mockKesselPermission(WorkspacePermission permission, Allowed allowed) {
        when(kesselCheckClient
            .check(kesselTestHelper.buildCheckRequest(DEFAULT_USER, permission)))
            .thenReturn(kesselTestHelper.buildCheckResponse(allowed));
    }
}
