package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.RbacTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestRbacTemplate extends EmailTemplatesInDbHelper {

    static final String RH_NEW_ROLE_AVAILABLE = "rh-new-role-available";
    static final String RH_PLATFORM_DEFAULT_ROLE_UPDATED = "rh-platform-default-role-updated";
    static final String RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED = "rh-non-platform-default-role-updated";
    static final String CUSTOM_ROLE_CREATED = "custom-role-created";
    static final String CUSTOM_ROLE_UPDATED = "custom-role-updated";
    static final String CUSTOM_ROLE_DELETED = "custom-role-deleted";
    static final String RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS = "rh-new-role-added-to-default-access";
    static final String RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS = "rh-role-removed-from-default-access";
    static final String CUSTOM_DEFAULT_ACCESS_UPDATED = "custom-default-access-updated";
    static final String GROUP_CREATED = "group-created";
    static final String GROUP_UPDATED = "group-updated";
    static final String GROUP_DELETED = "group-deleted";
    static final String PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM = "platform-default-group-turned-into-custom";
    static final String REQUEST_ACCESS = "request-access";

    static String[] RBAC_EVENT_TYPE_NAMES() {
        return new String[]{RH_NEW_ROLE_AVAILABLE, RH_PLATFORM_DEFAULT_ROLE_UPDATED, RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED, CUSTOM_ROLE_CREATED,
            CUSTOM_ROLE_UPDATED, CUSTOM_ROLE_DELETED, RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS, RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS,
            CUSTOM_DEFAULT_ACCESS_UPDATED, GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED, PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM,
            REQUEST_ACCESS};
    }

    @Override
    protected String getBundle() {
        return "console";
    }

    @Override
    protected String getApp() {
        return "rbac";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(RBAC_EVENT_TYPE_NAMES());
    }

    @MethodSource("RBAC_EVENT_TYPE_NAMES")
    @ParameterizedTest
    void shouldTestAllEventTypeTemplateTitles(String eventType) {
        Action action = RbacTestHelpers.createRbacAction();

        String result = generateEmailSubject(eventType, action);
        testTitle(eventType, result);
    }

    @MethodSource("RBAC_EVENT_TYPE_NAMES")
    @ParameterizedTest
    void shouldTestAllEventTypeTemplateBodies(String eventType) {
        Action action = RbacTestHelpers.createRbacAction();
        String result = generateEmailBody(eventType, action);
        testBody(eventType, result);
    }


    private void testTitle(String eventType, String result) {
        switch (eventType) {
            case RH_NEW_ROLE_AVAILABLE:
                assertEquals("Instant notification - Red Hat now provides a new role - User Access - Console", result);
                break;
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertEquals("Instant notification - Platform default role updated by Red Hat - User Access - Console", result);
                break;
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertEquals("Instant notification - Role updated by Red Hat - User Access - Console", result);
                break;
            case CUSTOM_ROLE_CREATED:
                assertEquals("Instant notification - Custom role created - User Access - Console", result);
                break;
            case CUSTOM_ROLE_UPDATED:
                assertEquals("Instant notification - Custom role updated - User Access - Console", result);
                break;
            case CUSTOM_ROLE_DELETED:
                assertEquals("Instant notification - Custom role deleted - User Access - Console", result);
                break;
            case RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS:
                assertEquals("Instant notification - Red Hat added a role to platform default access group - User Access - Console", result);
                break;
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                assertEquals("Instant notification - Red Hat removed a role from platform default access group - User Access - Console", result);
                break;
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                assertEquals("Instant notification - Custom platform access group updated - User Access - Console", result);
                break;
            case GROUP_CREATED:
                assertEquals("Instant notification - Custom group created - User Access - Console", result);
                break;
            case GROUP_UPDATED:
                assertEquals("Instant notification - Custom group updated - User Access - Console", result);
                break;
            case GROUP_DELETED:
                assertEquals("Instant notification - Custom group deleted - User Access - Console", result);
                break;
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                assertEquals("Instant notification - Platform default group is turned into custom - User Access - Console", result);
                break;
            case REQUEST_ACCESS:
                assertEquals("Instant notification - Request access - User Access - Console", result);
                break;
            default:
                break;
        }
    }

    private void testBody(String eventType, String result) {
        switch (eventType) {
            case RH_NEW_ROLE_AVAILABLE:
                assertTrue(result.contains("Red Hat now provides a new role"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertTrue(result.contains("Red Hat has updated the role"));
                assertTrue(result.contains("The role belongs to the platform default access group and"));
                assertTrue(result.contains("will be inherited by all users within your account."));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertTrue(result.contains("Red Hat has updated the role"));
                assertTrue(result.contains("The role does not belong to the platform default access group."));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case CUSTOM_ROLE_CREATED:
                assertTrue(result.contains("custom role"));
                assertTrue(result.contains("has been created by"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case CUSTOM_ROLE_UPDATED:
                assertTrue(result.contains("has been updated by"));
                assertTrue(result.contains("Custom role"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case CUSTOM_ROLE_DELETED:
                assertTrue(result.contains("Custom role"));
                assertTrue(result.contains("has been deleted by"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS:
                assertTrue(result.contains("Red Hat added a role"));
                assertTrue(result.contains("to platform default access group."));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                assertTrue(result.contains("Red Hat removed a role"));
                assertTrue(result.contains("from platform default access group"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                assertTrue(result.contains("Custom platform default access group has been updated by"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case GROUP_CREATED:
                assertTrue(result.contains("A custom group"));
                assertTrue(result.contains("has been created by"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case GROUP_UPDATED:
                assertTrue(result.contains("Custom group has been updated by"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case GROUP_DELETED:
                assertTrue(result.contains("Custom group"));
                assertTrue(result.contains("has been deleted by"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                assertTrue(result.contains("Platform default group is modified by"));
                assertTrue(result.contains("Red Hat will not be responsible for managing it from now on."));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case REQUEST_ACCESS:
                assertTrue(result.contains("Request for access received"));
                assertTrue(result.contains("within console.redhat.com. Please review this request and decide whether to grant or deny access."));
                assertTrue(result.contains("Granting Access:"));
                assertTrue(result.contains("Denying Access:"));
                assertTrue(result.contains("https://console.redhat.com/stuff"));
                assertTrue(result.contains("I want access to stuff"));
                assertTrue(result.contains("testUser AT somewhere"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            default:
                break;
        }
    }
}
