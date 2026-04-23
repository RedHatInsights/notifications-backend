package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import helpers.RbacTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestRbacTemplate {

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
    static final String TAM_ACCESS_REQUEST = "rh-new-tam-request-created";

    @Inject
    TestHelpers testHelpers;

    @ValueSource(strings = { RH_NEW_ROLE_AVAILABLE, RH_PLATFORM_DEFAULT_ROLE_UPDATED, RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED, CUSTOM_ROLE_CREATED, CUSTOM_ROLE_UPDATED, CUSTOM_ROLE_DELETED, RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS, RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS, CUSTOM_DEFAULT_ACCESS_UPDATED, GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED, PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM, REQUEST_ACCESS, TAM_ACCESS_REQUEST })
    @ParameterizedTest
    void testRenderedTemplates(final String eventType) {
        Action action = RbacTestHelpers.createRbacAction();
        String result = renderTemplate(eventType, action);
        checkResult(eventType, result);
    }

    private void checkResult(String eventType, String result) {
        switch (eventType) {
            case RH_NEW_ROLE_AVAILABLE:
                assertEquals("Red Hat now provides a new role **[testRoleName](https://localhost/iam/user-access/roles/detail/616ace4f-6024-4197-868a-2d0a2ac61286)**.", result);
                break;
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertEquals("Red Hat has updated the role **[testRoleName](https://localhost/iam/user-access/roles/detail/616ace4f-6024-4197-868a-2d0a2ac61286)**.", result);
                break;
            case CUSTOM_ROLE_CREATED:
                assertEquals("A custom role **[testRoleName](https://localhost/iam/user-access/roles/detail/616ace4f-6024-4197-868a-2d0a2ac61286)** has been created by testUser1.", result);
                break;
            case CUSTOM_ROLE_UPDATED:
                assertEquals("Custom role **[testRoleName](https://localhost/iam/user-access/roles/detail/616ace4f-6024-4197-868a-2d0a2ac61286)** has been updated by testUser1.", result);
                break;
            case CUSTOM_ROLE_DELETED:
                assertEquals("Custom role **testRoleName** has been deleted by testUser1 and is not available anymore. [Open User Access](https://localhost/iam/user-access/overview)", result);
                break;
            case RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS:
                assertEquals("Red Hat added a role **[myRole](https://localhost/iam/user-access/roles/detail/90d52d8b-614d-40f6-b073-1a88ee575f75)** to platform default access group.", result);
                break;
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                assertEquals("Red Hat removed a role **[myRole](https://localhost/iam/user-access/roles/detail/90d52d8b-614d-40f6-b073-1a88ee575f75)** from platform default access group.", result);
                break;
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                assertEquals("Custom platform default access group has been updated by testUser1. [Open User Access](https://localhost/iam/user-access/overview)", result);
                break;
            case GROUP_CREATED:
                assertEquals("A custom group **[testRoleName](https://localhost/iam/user-access/groups/detail/616ace4f-6024-4197-868a-2d0a2ac61286)** has been created by testUser1.", result);
                break;
            case GROUP_UPDATED:
                assertEquals("Custom group has been updated by testUser1. [Open group details](https://localhost/iam/user-access/groups/detail/616ace4f-6024-4197-868a-2d0a2ac61286)", result);
                break;
            case GROUP_DELETED:
                assertEquals("Custom group **testRoleName** has been deleted by testUser1 and is not available anymore. [Open User Access](https://localhost/iam/user-access/overview)", result);
                break;
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                assertEquals("Platform default group is modified by testUser1 and Red Hat will not be responsible for managing it from now on. [Open User Access](https://localhost/iam/user-access/overview)", result);
                break;
            case REQUEST_ACCESS:
                assertEquals("**testUser1** has requested access to **[https://console.redhat.com/stuff](https://console.redhat.com/stuff)**.", result);
                break;
            case TAM_ACCESS_REQUEST:
                assertEquals("A Technical Account Manager **testUser1** requested to access your account. [Open User Access](https://localhost/iam/user-access/overview)", result);
                break;
            default:
                break;
        }
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "console", "rbac", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
