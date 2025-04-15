package com.redhat.cloud.notifications.auth.annotation;

import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization;
import com.redhat.cloud.notifications.auth.kessel.KesselTestHelper;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
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
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.project_kessel.api.relations.v1beta1.CheckResponse;
import org.project_kessel.inventory.client.KesselCheckClient;
import org.project_kessel.relations.client.CheckClient;

import java.lang.reflect.Method;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;

@QuarkusTest
public class AuthorizationInterceptorTest {
    @Inject
    KesselTestHelper kesselTestHelper;

    @Inject
    KesselAuthorization kesselAuthorization;

    @Inject
    KesselInventoryAuthorization kesselInventoryAuthorization;

    @InjectMock
    BackendConfig backendConfig;

    /**
     * Required for the {@link KesselTestHelper}.
     */
    @InjectMock
    CheckClient checkClient;

    /**
     * Required for the {@link KesselTestHelper}.
     */
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
        this.authorizationInterceptor = new AuthorizationInterceptor(this.backendConfig, this.kesselAuthorization, this.workspaceUtils, this.kesselInventoryAuthorization);
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
     * when the principal does hot havethe expected legacy RBAC role.
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

        // Make sure that Kessel relations is disabled for this test.
        this.kesselTestHelper.mockKesselRelations(false, false);

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
     * enabled as the authorization back end, and no {@link IntegrationPermission}
     * or {@link WorkspacePermission} elements were defined in the annotation.
     *
     * @throws NoSuchMethodException when the method's specification is not
     * properly defined, and therefore we cannot get it to perform the tests.
     */
    @Test
    void testMissingIntegrationWorkspacePermissions() throws NoSuchMethodException {
        // Mock the invocation context and make it return one of our defined
        // methods in this class, for simplicity.
        final InvocationContext invocationContext = Mockito.mock(InvocationContext.class);

        final Method methodUnderTest = AuthorizationInterceptorHelper.class.getMethod("testMethodWithRBACRole", SecurityContext.class, String.class, UUID.class);
        Mockito.when(invocationContext.getMethod()).thenReturn(methodUnderTest);

        // Make sure that Kessel relations is enabled for this test.
        this.kesselTestHelper.mockKesselRelations(true, false);

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
            String.format("No integration or workspace permissions were set for method \"%s\", and at least one of them is required for the \"KesselRequiredPermission\" annotation to work", methodUnderTest.getName()),
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

        // Make sure that Kessel relations is enabled for this test.
        this.kesselTestHelper.mockKesselRelations(true, false);

        // Mock the returned parameters by the context, to be able to provide
        // whichever security context we want to the interceptor.
        final SecurityContext securityContext = this.mockSecurityContext();
        Mockito.when(invocationContext.getParameters()).thenReturn(new Object[]{securityContext});

        // Mock the Kessel checks to simulate that the principal has the
        // required workspace permissions.
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BUNDLES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.APPLICATIONS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

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

        // Make sure that Kessel relations is enabled for this test.
        this.kesselTestHelper.mockKesselRelations(true, false);

        // Mock the returned parameters by the context, to be able to provide
        // whichever security context we want to the interceptor.
        final SecurityContext securityContext = this.mockSecurityContext();
        Mockito.when(invocationContext.getParameters()).thenReturn(new Object[]{securityContext});

        // Mock the Kessel checks to simulate that the principal has the
        // required workspace permissions.
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BUNDLES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.APPLICATIONS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString(), CheckResponse.Allowed.ALLOWED_FALSE);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        // Call the function under test which should throw a ForbiddenException.
        Assertions.assertThrows(
            ForbiddenException.class,
            () -> this.authorizationInterceptor.aroundInvoke(invocationContext)
        );
    }

    /**
     * Tests that the function under test throws an exception when an
     * {@link IntegrationPermission} is specified, but the {@link IntegrationId}
     * annotation has not been set in the method.
     *
     * @throws Exception when an unexpected error occurs.
     * @throws NoSuchMethodException when the method's specification is not
     * properly defined, and therefore we cannot get it to perform the tests.
     */
    @Test
    void testMissingIntegrationIdAnnotation() throws Exception, NoSuchMethodException {
        // Mock the invocation context and make it return one of our defined
        // methods in this class, for simplicity.
        final InvocationContext invocationContext = Mockito.mock(InvocationContext.class);

        final Method methodUnderTest = AuthorizationInterceptorHelper.class.getMethod("testMethodWithWorkspaceAndIntegrationPermissionsMissingIntegrationId", SecurityContext.class, String.class, UUID.class);
        Mockito.when(invocationContext.getMethod()).thenReturn(methodUnderTest);

        // Make sure that Kessel relations is enabled for this test.
        this.kesselTestHelper.mockKesselRelations(true, false);

        // Mock the returned parameters by the context, to be able to provide
        // whichever security context and integration ID we want to the
        // interceptor.
        final SecurityContext securityContext = this.mockSecurityContext();
        final String secondIgnoredParameter = "second-ignored-parameter";
        final UUID integrationId = UUID.randomUUID();
        Mockito.when(invocationContext.getParameters()).thenReturn(new Object[]{securityContext, secondIgnoredParameter, integrationId});

        // Mock the Kessel checks to simulate that the principal has the
        // required workspace permissions.
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BUNDLES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.APPLICATIONS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        // Mock the Kessel checks to simulate that the principal has the
        // required integration permissions.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW, ResourceType.INTEGRATION, integrationId.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW_HISTORY, ResourceType.INTEGRATION, integrationId.toString());

        // Call the function under test which should throw an exception that
        // signals that the method is missing a parameter annotated with the
        // "IntegrationId" annotation.
        final IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.authorizationInterceptor.aroundInvoke(invocationContext)
        );

        // Assert that the expected exception was thrown.
        Assertions.assertEquals(
            String.format("The integration ID is not annotated on the method \"%s\", which is needed for the \"KesselRequiredPermission\" annotation to work", methodUnderTest.getName()),
            exception.getMessage()
        );
    }

    /**
     * Tests that the function under test throws a {@link jakarta.ws.rs.NotFoundException}
     * when the principal does not have authorization with one of the
     * {@link IntegrationPermission}s.
     *
     * @throws Exception when an unexpected error occurs.
     * @throws NoSuchMethodException when the method's specification is not
     * properly defined, and therefore we cannot get it to perform the tests.
     */
    @Test
    void testIntegrationPermissionDenied() throws Exception, NoSuchMethodException {
        // Mock the invocation context and make it return one of our defined
        // methods in this class, for simplicity.
        final InvocationContext invocationContext = Mockito.mock(InvocationContext.class);

        final Method methodUnderTest = AuthorizationInterceptorHelper.class.getMethod("testMethodWithWorkspaceAndIntegrationPermissions", SecurityContext.class, String.class, UUID.class);
        Mockito.when(invocationContext.getMethod()).thenReturn(methodUnderTest);

        // Make sure that Kessel relations is enabled for this test.
        this.kesselTestHelper.mockKesselRelations(true, false);

        // Mock the returned parameters by the context, to be able to provide
        // whichever security context and integration ID we want to the
        // interceptor.
        final SecurityContext securityContext = this.mockSecurityContext();
        final String secondIgnoredParameter = "second-ignored-parameter";
        final UUID integrationId = UUID.randomUUID();
        Mockito.when(invocationContext.getParameters()).thenReturn(new Object[]{securityContext, secondIgnoredParameter, integrationId});

        // Mock the Kessel checks to simulate that the principal has the
        // required workspace permissions.
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BUNDLES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.APPLICATIONS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        // Mock the Kessel checks to simulate that the principal does not have
        // all the required integration permissions.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW, ResourceType.INTEGRATION, integrationId.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW_HISTORY, ResourceType.INTEGRATION, integrationId.toString(), CheckResponse.Allowed.ALLOWED_FALSE);

        // Call the function under test which should throw a ForbiddenException.
        Assertions.assertThrows(
            NotFoundException.class,
            () -> this.authorizationInterceptor.aroundInvoke(invocationContext)
        );
    }

    /**
     * Tests that the function under test does not throw an exception when
     * the principal has the specified {@link WorkspacePermission} and {@link IntegrationPermission}.
     *
     * @throws Exception when an unexpected error occurs.
     * @throws NoSuchMethodException when the method's specification is not
     * properly defined, and therefore we cannot get it to perform the tests.
     */
    @Test
    void testIntegrationPermissionGranted() throws Exception, NoSuchMethodException {
        // Mock the invocation context and make it return one of our defined
        // methods in this class, for simplicity.
        final InvocationContext invocationContext = Mockito.mock(InvocationContext.class);

        final Method methodUnderTest = AuthorizationInterceptorHelper.class.getMethod("testMethodWithWorkspaceAndIntegrationPermissions", SecurityContext.class, String.class, UUID.class);
        Mockito.when(invocationContext.getMethod()).thenReturn(methodUnderTest);

        // Make sure that Kessel relations is enabled for this test.
        this.kesselTestHelper.mockKesselRelations(true, false);

        // Mock the returned parameters by the context, to be able to provide
        // whichever security context and integration ID we want to the
        // interceptor.
        final SecurityContext securityContext = this.mockSecurityContext();
        final String secondIgnoredParameter = "second-ignored-parameter";
        final UUID integrationId = UUID.randomUUID();
        Mockito.when(invocationContext.getParameters()).thenReturn(new Object[]{securityContext, secondIgnoredParameter, integrationId});

        // Mock the Kessel checks to simulate that the principal has the
        // required workspace permissions.
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BUNDLES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.APPLICATIONS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        // Mock the Kessel checks to simulate that the principal has the
        // required integration permissions.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW, ResourceType.INTEGRATION, integrationId.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW_HISTORY, ResourceType.INTEGRATION, integrationId.toString());

        // Call the function under test which should not throw any exceptions.
        this.authorizationInterceptor.aroundInvoke(invocationContext);
    }
}
