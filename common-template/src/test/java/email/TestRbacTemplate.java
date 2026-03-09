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
        testBody(eventType, result, false);
        result = generateEmailBody(eventType, action, true);
        testBody(eventType, result, true);
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

    private void testBody(String eventType, String result, boolean useBetaTemplate) {
        switch (eventType) {
            case RH_NEW_ROLE_AVAILABLE:
                assertTrue(result.contains("Red Hat now provides the"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertTrue(result.contains("Red Hat updated the"));
                assertTrue(result.contains("The role belongs to the"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertTrue(result.contains("Red Hat updated the"));
                assertTrue(result.contains("The role does not belong to the"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case CUSTOM_ROLE_CREATED:
                assertTrue(result.contains("Custom role created"));
                assertTrue(result.contains("created the"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case CUSTOM_ROLE_UPDATED:
                assertTrue(result.contains("Custom role updated"));
                assertTrue(result.contains("updated the"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case CUSTOM_ROLE_DELETED:
                assertTrue(result.contains("Custom role deleted"));
                assertTrue(result.contains("deleted the"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS:
                assertTrue(result.contains("Red Hat added the"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                assertTrue(result.contains("Red Hat removed the"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                assertTrue(result.contains("Custom default access group updated"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case GROUP_CREATED:
                assertTrue(result.contains("Group created"));
                assertTrue(result.contains("created the"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case GROUP_UPDATED:
                assertTrue(result.contains("role was added to the group."));
                assertTrue(result.contains("user was removed from the group."));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case GROUP_DELETED:
                assertTrue(result.contains("Group deleted"));
                assertTrue(result.contains("deleted the"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                assertTrue(result.contains("Platform default group turned into custom"));
                assertTrue(result.contains("It will receive no further updates from Red Hat Hybrid Cloud Console."));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case REQUEST_ACCESS:
                assertTrue(result.contains("Access requested"));
                assertTrue(result.contains("A user within your organization requested"));
                assertTrue(result.contains("Granting Access:"));
                assertTrue(result.contains("Denying Access:"));
                assertTrue(result.contains("https://console.redhat.com/stuff"));
                assertTrue(result.contains("I want access to stuff"));
                assertTrue(result.contains("testUser AT somewhere"));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            case RH_TAM_ACCESS_REQUESTED:
                assertTrue(result.contains("New TAM access request"));
                assertTrue(result.contains("A Red Hat technical account manager requested access to your account."));
                assertTrue(result.contains("to review the request."));
                assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                break;
            default:
                break;
        }
    }
}
