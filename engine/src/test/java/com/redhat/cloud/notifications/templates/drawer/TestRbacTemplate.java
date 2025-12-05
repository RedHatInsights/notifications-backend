package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.RbacTestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestRbacTemplate extends IntegrationTemplatesInDbHelper {


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
        return List.of(RH_NEW_ROLE_AVAILABLE, RH_PLATFORM_DEFAULT_ROLE_UPDATED, RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED, CUSTOM_ROLE_CREATED, CUSTOM_ROLE_UPDATED, CUSTOM_ROLE_DELETED, RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS, RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS, CUSTOM_DEFAULT_ACCESS_UPDATED, GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED, PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM);
    }

    @ValueSource(strings = { RH_NEW_ROLE_AVAILABLE, RH_PLATFORM_DEFAULT_ROLE_UPDATED, RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED, CUSTOM_ROLE_CREATED, CUSTOM_ROLE_UPDATED, CUSTOM_ROLE_DELETED, RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS, RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS, CUSTOM_DEFAULT_ACCESS_UPDATED, GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED, PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM })
    @ParameterizedTest
    void testRenderedTemplates(String eventType) {
        Action action = RbacTestHelpers.createRbacAction();
        String result = generateDrawerTemplate(eventType, action);
        checkResult(eventType, result);
    }

    private void checkResult(String eventType, String result) {
        switch (eventType) {
            case RH_NEW_ROLE_AVAILABLE:
                assertEquals("Red Hat now provides a new role **testRoleName**.", result);
                break;
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertEquals("Red Hat has updated the role **testRoleName**.", result);
                break;
            case CUSTOM_ROLE_CREATED:
                assertEquals("A custom role **testRoleName** has been created by testUser1.", result);
                break;
            case CUSTOM_ROLE_UPDATED:
                assertEquals("Custom role **testRoleName** has been updated by testUser1.", result);
                break;
            case CUSTOM_ROLE_DELETED:
                assertEquals("Custom role **testRoleName** has been deleted by testUser1 and is not available anymore.", result);
                break;
            case RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS:
                assertEquals("Red Hat added a role **myRole** to platform default access group.", result);
                break;
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                assertEquals("Red Hat removed a role **myRole** from platform default access group.", result);
                break;
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                assertEquals("Custom platform default access group has been updated by testUser1.", result);
                break;
            case GROUP_CREATED:
                assertEquals("A custom group **testRoleName** has been created by testUser1.", result);
                break;
            case GROUP_UPDATED:
                assertEquals("Custom group has been updated by testUser1.", result);
                break;
            case GROUP_DELETED:
                assertEquals("Custom group **testRoleName** has been deleted by testUser1 and is not available anymore.", result);
                break;
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                assertEquals("Platform default group is modified by testUser1 and Red Hat will not be responsible for managing it from now on.", result);
                break;
            default:
                break;
        }
    }
}
