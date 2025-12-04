package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.ingress.Type;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.SecurityContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;

import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.COUNTER_TAG_FAILURES;
import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.COUNTER_TAG_REQUEST_RESULT;
import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.COUNTER_TAG_SUCCESSES;
import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME;
import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
public class KesselInventoryAuthorizationTest {
    @InjectSpy
    BackendConfig backendConfig;

    @InjectMock
    KesselCheckClient checkClient;

    @Inject
    KesselInventoryAuthorization kesselAuthorization;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @BeforeEach
    void beforeEach() {
        // save counter values
        saveCounterValues();
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * does not raise an exception.
     */
    @Test
    void testAuthorized() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselEnabled(anyString())).thenReturn(true);

        // Simulate that Kessel returns a positive response.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build();
        Mockito.when(this.checkClient.check(Mockito.any())).thenReturn(positiveCheckResponse);

        // Call the function under test.
        this.kesselAuthorization.hasPermissionOnResource(
            mockedSecurityContext,
            WorkspacePermission.EVENTS_VIEW,
            KesselInventoryResourceType.WORKSPACE,
            "workspace-uuid"
        );

        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).check(Mockito.any());

        // Assert counter values
        assertCounterIncrements(1, 0, 0, 0);
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * returns true.
     */
    @Test
    void testAuthorizedCriterion() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselEnabled(anyString())).thenReturn(true);

        // Simulate that Kessel returns a positive response.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_TRUE).build();
        Mockito.when(this.checkClient.check(Mockito.any())).thenReturn(positiveCheckResponse);
        RecipientsAuthorizationCriterion authorizationCriterion = new RecipientsAuthorizationCriterion();
        authorizationCriterion.setId("workspace-uuid");
        authorizationCriterion.setRelation(WorkspacePermission.EVENTS_VIEW.getKesselPermissionName());
        Type t = new Type();
        t.setNamespace("rbac");
        t.setName("workspace");
        authorizationCriterion.setType(t);

        // Call the function under test.
        boolean isAuthorized = this.kesselAuthorization.hasPermissionOnResource(
            mockedSecurityContext,
            authorizationCriterion
        );

        assertTrue(isAuthorized);
        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).check(Mockito.any());

        // Assert counter values
        assertCounterIncrements(1, 0, 0, 0);
    }

    /**
     * Tests failure counter increments in case of exception.
     */
    @Test
    void testFailureCounterIncrements() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Simulate that Kessel returns an exception
        Mockito.when(this.checkClient.check(Mockito.any())).thenThrow(RuntimeException.class);

        // Call the function under test.
        Assertions.assertThrows(
            RuntimeException.class,
            () -> this.kesselAuthorization.hasPermissionOnResource(
                    mockedSecurityContext,
                    WorkspacePermission.EVENTS_VIEW,
                    KesselInventoryResourceType.WORKSPACE,
                    "workspace-uuid"
            )
        );

        // Assert counter values
        assertCounterIncrements(0, 1, 0, 0);
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * throws an exception.
     */
    @Test
    void testUnauthorized() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselEnabled(anyString())).thenReturn(true);

        // Simulate that Kessel returns a negative response.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_FALSE).build();
        Mockito.when(this.checkClient.check(Mockito.any())).thenReturn(positiveCheckResponse);

        // Call the function under test and expect that it throws a "Forbidden"
        // exception.
        Assertions.assertThrows(
            ForbiddenException.class,
            () -> this.kesselAuthorization.hasPermissionOnResource(
                mockedSecurityContext,
                WorkspacePermission.EVENTS_VIEW,
                KesselInventoryResourceType.WORKSPACE,
                "workspace-uuid"
            ),
            "unexpected exception thrown, as with a negative response from Kessel it should throw a \"Forbidden exception\""
        );

        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).check(Mockito.any());

        // Assert counter values
        assertCounterIncrements(1, 0, 0, 0);
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * returns false.
     */
    @Test
    void testUnauthorizedCriterion() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselEnabled(anyString())).thenReturn(true);

        // Simulate that Kessel returns a negative response.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(Allowed.ALLOWED_FALSE).build();
        Mockito.when(this.checkClient.check(Mockito.any())).thenReturn(positiveCheckResponse);

        RecipientsAuthorizationCriterion authorizationCriterion = new RecipientsAuthorizationCriterion();
        authorizationCriterion.setId("workspace-uuid");
        authorizationCriterion.setRelation(WorkspacePermission.EVENTS_VIEW.getKesselPermissionName());
        Type t = new Type();
        t.setNamespace("rbac");
        t.setName("workspace");
        authorizationCriterion.setType(t);

        // Call the function under test.
        boolean isAuthorized = this.kesselAuthorization.hasPermissionOnResource(
            mockedSecurityContext,
            authorizationCriterion
        );

        assertFalse(isAuthorized);
        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).check(Mockito.any());

        // Assert counter values
        assertCounterIncrements(1, 0, 0, 0);
    }

    /**
     * Tests that when Kessel check fails, the function under test
     * returns false.
     */
    @Test
    void testUnauthorizedCriterionBecauseOfException() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselEnabled(anyString())).thenReturn(true);

        // Simulate that Kessel returns an exception
        Mockito.when(this.checkClient.check(Mockito.any())).thenThrow(RuntimeException.class);

        RecipientsAuthorizationCriterion authorizationCriterion = new RecipientsAuthorizationCriterion();
        authorizationCriterion.setId("workspace-uuid");
        authorizationCriterion.setRelation(WorkspacePermission.EVENTS_VIEW.getKesselPermissionName());
        Type t = new Type();
        t.setNamespace("rbac");
        t.setName("workspace");
        authorizationCriterion.setType(t);

        // Call the function under test.
        boolean isAuthorized = this.kesselAuthorization.hasPermissionOnResource(
            mockedSecurityContext,
            authorizationCriterion
        );

        assertFalse(isAuthorized);
        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).check(Mockito.any());

        // Assert counter values
        assertCounterIncrements(0, 1, 0, 0);
    }

    /**
     * Mock the security context.
     */
    private static @NotNull SecurityContext initMockedSecurityContextWithRhIdentity() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Create a RhIdentity principal and assign it to the mocked security
        // context.
        final RhIdentity identity = Mockito.mock(RhIdentity.class);
        Mockito.when(identity.getName()).thenReturn("Red Hat user");

        final ConsolePrincipal<?> principal = new RhIdPrincipal(identity);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(principal);
        return mockedSecurityContext;
    }

    private void saveCounterValues() {
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KesselInventoryAuthorization.KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES);
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES);
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES);
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES);
    }

    private void assertCounterIncrements(final int expectedPermissionCheckSuccesses, final int expectedPermissionCheckFailures, final int expectedLookupResourcesSuccesses, int expectedLookupResourcesFailures) {
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES, expectedPermissionCheckSuccesses);
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, expectedPermissionCheckFailures);
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES, expectedLookupResourcesSuccesses);
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, expectedLookupResourcesFailures);
    }
}
