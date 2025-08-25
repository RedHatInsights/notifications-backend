package email;

import com.redhat.cloud.notifications.ingress.Action;
import helpers.RbacTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestRbacTemplate extends EmailTemplatesRendererHelper {

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
    static final String RH_TAM_ACCESS_REQUESTED = "rh-new-tam-request-created";

    static Stream<Arguments> RBAC_EVENT_TYPE_NAMES() {
        return Stream.of(
            Arguments.of(RH_NEW_ROLE_AVAILABLE, "New Red Hat role available"),
            Arguments.of(RH_PLATFORM_DEFAULT_ROLE_UPDATED, "Red Hat role in platform default access group updated"),
            Arguments.of(RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED, "Red Hat role not in platform default access group updated"),
            Arguments.of(CUSTOM_ROLE_CREATED, "Custom role created"),
            Arguments.of(CUSTOM_ROLE_UPDATED, "Custom role updated"),
            Arguments.of(CUSTOM_ROLE_DELETED, "Custom role deleted"),
            Arguments.of(RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS, "New Red Hat role added to platform default access group"),
            Arguments.of(RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS, "Red Hat role removed from platform default access group"),
            Arguments.of(CUSTOM_DEFAULT_ACCESS_UPDATED, "Custom platform default access group updated"),
            Arguments.of(GROUP_CREATED, "Group created"),
            Arguments.of(GROUP_UPDATED, "Group updated"),
            Arguments.of(GROUP_DELETED, "Group deleted"),
            Arguments.of(PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM, "Platform default access group turned into custom"),
            Arguments.of(REQUEST_ACCESS, "Request access"),
            Arguments.of(RH_TAM_ACCESS_REQUESTED, "New TAM access request created")
        );
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
    protected String getBundleDisplayName() {
        return "Console";
    }

    @Override
    protected String getAppDisplayName() {
        return "User Access";
    }

    @MethodSource("RBAC_EVENT_TYPE_NAMES")
    @ParameterizedTest
    void shouldTestAllEventTypeTemplateTitles(String eventType, String eventTypeDispName) {
        Action action = RbacTestHelpers.createRbacAction();
        eventTypeDisplayName = eventTypeDispName;
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
                assertEquals("Instant notification - New Red Hat role available - User Access - Console", result);
                break;
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertEquals("Instant notification - Red Hat role in platform default access group updated - User Access - Console", result);
                break;
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertEquals("Instant notification - Red Hat role not in platform default access group updated - User Access - Console", result);
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
                assertEquals("Instant notification - New Red Hat role added to platform default access group - User Access - Console", result);
                break;
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                assertEquals("Instant notification - Red Hat role removed from platform default access group - User Access - Console", result);
                break;
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                assertEquals("Instant notification - Custom platform default access group updated - User Access - Console", result);
                break;
            case GROUP_CREATED:
                assertEquals("Instant notification - Group created - User Access - Console", result);
                break;
            case GROUP_UPDATED:
                assertEquals("Instant notification - Group updated - User Access - Console", result);
                break;
            case GROUP_DELETED:
                assertEquals("Instant notification - Group deleted - User Access - Console", result);
                break;
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                assertEquals("Instant notification - Platform default access group turned into custom - User Access - Console", result);
                break;
            case REQUEST_ACCESS:
                assertEquals("Instant notification - Request access - User Access - Console", result);
                break;
            case RH_TAM_ACCESS_REQUESTED:
                assertEquals("Instant notification - New TAM access request created - User Access - Console", result);
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
            case RH_TAM_ACCESS_REQUESTED:
                assertTrue(result.contains("New TAM Access Request"));
                assertTrue(result.contains("A technical account manager requested to access your account."));
                assertTrue(result.contains("Check the request in Insights"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            default:
                break;
        }
    }
}
