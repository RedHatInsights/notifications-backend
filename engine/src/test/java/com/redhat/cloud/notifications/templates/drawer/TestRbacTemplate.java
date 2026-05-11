package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.DrawerTemplatesHelper;
import com.redhat.cloud.notifications.RbacTestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestRbacTemplate extends DrawerTemplatesHelper {


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
        return List.of(RH_NEW_ROLE_AVAILABLE, RH_PLATFORM_DEFAULT_ROLE_UPDATED, RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED, CUSTOM_ROLE_CREATED, CUSTOM_ROLE_UPDATED, CUSTOM_ROLE_DELETED, RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS, RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS, CUSTOM_DEFAULT_ACCESS_UPDATED, GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED, PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM, REQUEST_ACCESS, TAM_ACCESS_REQUEST);
    }

    @ValueSource(strings = { RH_NEW_ROLE_AVAILABLE, RH_PLATFORM_DEFAULT_ROLE_UPDATED, RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED, CUSTOM_ROLE_CREATED, CUSTOM_ROLE_UPDATED, CUSTOM_ROLE_DELETED, RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS, RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS, CUSTOM_DEFAULT_ACCESS_UPDATED, GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED, PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM, REQUEST_ACCESS, TAM_ACCESS_REQUEST })
    @ParameterizedTest
    void testRenderedTemplates(String eventType) {
        Action action = RbacTestHelpers.createRbacAction();
        String result = generateDrawerTemplate(eventType, action);
        checkResult(eventType, result);
    }

    private void checkResult(String eventType, String result) {
        switch (eventType) {
            case RH_NEW_ROLE_AVAILABLE:
                assertEquals("Red Hat now provides the **[testRoleName](https://localhost/iam/user-access/roles/detail/d69ef18d-22d8-4b08-ac9b-096d13148407?from=notifications&integration=drawer)** role.", result);
                break;
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertEquals("Red Hat updated the **[testRoleName](https://localhost/iam/user-access/roles/detail/d69ef18d-22d8-4b08-ac9b-096d13148407?from=notifications&integration=drawer)** role.", result);
                break;
            case CUSTOM_ROLE_CREATED:
                assertEquals("**[testUser1](https://localhost/iam/user-access/users/detail/testUser1?from=notifications&integration=drawer)** created the **[testRoleName](https://localhost/iam/user-access/roles/detail/d69ef18d-22d8-4b08-ac9b-096d13148407?from=notifications&integration=drawer)** custom role.", result);
                break;
            case CUSTOM_ROLE_UPDATED:
                assertEquals("**[testUser1](https://localhost/iam/user-access/users/detail/testUser1?from=notifications&integration=drawer)** updated the **[testRoleName](https://localhost/iam/user-access/roles/detail/d69ef18d-22d8-4b08-ac9b-096d13148407?from=notifications&integration=drawer)** custom role.", result);
                break;
            case CUSTOM_ROLE_DELETED:
                assertEquals("**[testUser1](https://localhost/iam/user-access/users/detail/testUser1?from=notifications&integration=drawer)** deleted the **testRoleName** custom role.", result);
                break;
            case RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS:
                assertEquals("Red Hat added the **[myRole](https://localhost/iam/user-access/roles/detail/90d52d8b-614d-40f6-b073-1a88ee575f75?from=notifications&integration=drawer)** role to the Default access group.", result);
                break;
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                assertEquals("Red Hat removed the **[myRole](https://localhost/iam/user-access/roles/detail/90d52d8b-614d-40f6-b073-1a88ee575f75?from=notifications&integration=drawer)** role from the Default access group.", result);
                break;
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                assertEquals("**[testUser1](https://localhost/iam/user-access/users/detail/testUser1?from=notifications&integration=drawer)** updated the Custom default access group by adding the **[myRole](https://localhost/iam/user-access/roles/detail/90d52d8b-614d-40f6-b073-1a88ee575f75?from=notifications&integration=drawer)** to the group.", result);
                break;
            case GROUP_CREATED:
                assertEquals("**[testUser1](https://localhost/iam/user-access/users/detail/testUser1?from=notifications&integration=drawer)** created the **[testRoleName](https://localhost/iam/user-access/groups/detail/d69ef18d-22d8-4b08-ac9b-096d13148407?from=notifications&integration=drawer)** group.", result);
                break;
            case GROUP_UPDATED:
                assertEquals("**[testUser1](https://localhost/iam/user-access/users/detail/testUser1?from=notifications&integration=drawer)** updated the **[testRoleName](https://localhost/iam/user-access/groups/detail/d69ef18d-22d8-4b08-ac9b-096d13148407?from=notifications&integration=drawer)** group.", result);
                break;
            case GROUP_DELETED:
                assertEquals("**[testUser1](https://localhost/iam/user-access/users/detail/testUser1?from=notifications&integration=drawer)** deleted the **testRoleName** group.", result);
                break;
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                assertEquals("**[testUser1](https://localhost/iam/user-access/users/detail/testUser1?from=notifications&integration=drawer)** changed the Default access group. The group is now called **[testRoleName](https://localhost/iam/user-access/groups/detail/d69ef18d-22d8-4b08-ac9b-096d13148407?from=notifications&integration=drawer)**.", result);
                break;
            case REQUEST_ACCESS:
                assertEquals("**[testUser1](https://localhost/iam/user-access/users/detail/testUser1?from=notifications&integration=drawer)** requested access to **[https://console.redhat.com/stuff](https://console.redhat.com/stuff?from=notifications&integration=drawer)**.", result);
                break;
            case TAM_ACCESS_REQUEST:
                assertEquals("A Red Hat technical account manager requested access to your account. Go to [Red Hat Access Request](https://localhost/iam/user-access/access-requests?from=notifications&integration=drawer) to review the request.", result);
                break;
            default:
                break;
        }
    }
}
