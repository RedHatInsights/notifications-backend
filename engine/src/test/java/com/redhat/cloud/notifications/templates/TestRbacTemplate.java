package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.RbacTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.inject.Inject;
import java.util.Map;

import static com.redhat.cloud.notifications.templates.Rbac.CUSTOM_DEFAULT_ACCESS_UPDATED;
import static com.redhat.cloud.notifications.templates.Rbac.CUSTOM_ROLE_CREATED;
import static com.redhat.cloud.notifications.templates.Rbac.CUSTOM_ROLE_DELETED;
import static com.redhat.cloud.notifications.templates.Rbac.CUSTOM_ROLE_UPDATED;
import static com.redhat.cloud.notifications.templates.Rbac.GROUP_CREATED;
import static com.redhat.cloud.notifications.templates.Rbac.GROUP_DELETED;
import static com.redhat.cloud.notifications.templates.Rbac.GROUP_UPDATED;
import static com.redhat.cloud.notifications.templates.Rbac.PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM;
import static com.redhat.cloud.notifications.templates.Rbac.RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS;
import static com.redhat.cloud.notifications.templates.Rbac.RH_NEW_ROLE_AVAILABLE;
import static com.redhat.cloud.notifications.templates.Rbac.RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED;
import static com.redhat.cloud.notifications.templates.Rbac.RH_PLATFORM_DEFAULT_ROLE_UPDATED;
import static com.redhat.cloud.notifications.templates.Rbac.RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestRbacTemplate {

    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = true;

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    Rbac rbac;

    @BeforeEach
    void beforeEach() {
        featureFlipper.setRbacEmailTemplatesV2Enabled(false);
    }

    @ValueSource(strings = { RH_NEW_ROLE_AVAILABLE, RH_PLATFORM_DEFAULT_ROLE_UPDATED, RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED, CUSTOM_ROLE_CREATED, CUSTOM_ROLE_UPDATED, CUSTOM_ROLE_DELETED, RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS, RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS, CUSTOM_DEFAULT_ACCESS_UPDATED, GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED, PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM })
    @ParameterizedTest
    void shouldTestAllEventTypeTemplateTitles(String eventType) {
        Action action = RbacTestHelpers.createRbacAction();
        String result = generateFromTemplate(rbac.getTitle(eventType, null), action);
        writeEmailTemplate(result, rbac.getTitle(eventType, null).getTemplate().getId());
        testTitle(eventType, result);

        featureFlipper.setRbacEmailTemplatesV2Enabled(true);
        result = generateFromTemplate(rbac.getTitle(eventType, null), action);
        writeEmailTemplate(result, rbac.getTitle(eventType, null).getTemplate().getId());
        testTitle(eventType, result);
    }

    @ValueSource(strings = { RH_NEW_ROLE_AVAILABLE, RH_PLATFORM_DEFAULT_ROLE_UPDATED, RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED, CUSTOM_ROLE_CREATED, CUSTOM_ROLE_UPDATED, CUSTOM_ROLE_DELETED, RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS, RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS, CUSTOM_DEFAULT_ACCESS_UPDATED, GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED, PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM })
    @ParameterizedTest
    void shouldTestAllEventTypeTemplateBodies(String eventType) {
        Action action = RbacTestHelpers.createRbacAction();
        String result = generateFromTemplate(rbac.getBody(eventType, null), action);
        writeEmailTemplate(result, rbac.getBody(eventType, null).getTemplate().getId());
        testBody(eventType, result);

        featureFlipper.setRbacEmailTemplatesV2Enabled(true);
        result = generateFromTemplate(rbac.getBody(eventType, null), action);
        writeEmailTemplate(result, rbac.getBody(eventType, null).getTemplate().getId());
        testBody(eventType, result);
    }


    private void testTitle(String eventType, String result) {
        switch (eventType) {
            case RH_NEW_ROLE_AVAILABLE:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Red Hat now provides a new role - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Red Hat now provides a new role"));
                }
                break;
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Platform default role updated by Red Hat - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Red Hat has updated the platform default role"));
                }
                break;
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Role updated by Red Hat - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Red Hat has updated the role"));
                }
                break;
            case CUSTOM_ROLE_CREATED:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Custom role created - User Access - Console", result);
                } else {
                    assertTrue(result.contains("A new custom role testRoleName has been created"));
                }
                break;
            case CUSTOM_ROLE_UPDATED:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Custom role updated - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Custom role testRoleName has been updated"));
                }
                break;
            case CUSTOM_ROLE_DELETED:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Custom role deleted - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Custom role testRoleName has been deleted"));
                }
                break;
            case RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Red Hat added a role to platform default access group - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Red Hat added a role myRole to platform default access group"));
                }
                break;
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Red Hat removed a role from platform default access group - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Red Hat removed a role myRole from platform default access group"));
                }
                break;
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Custom platform access group updated - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Custom platform access group testRoleName has been updated."));
                }
                break;
            case GROUP_CREATED:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Custom group created - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Custom group testRoleName has been created"));
                }
                break;
            case GROUP_UPDATED:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Custom group updated - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Custom group testRoleName has been updated"));
                }
                break;
            case GROUP_DELETED:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Custom group deleted - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Custom group testRoleName has been deleted"));
                }
                break;
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Platform default group is turned into custom - User Access - Console", result);
                } else {
                    assertTrue(result.contains("Platform default group is turned into custom"));
                }
                break;
            default:
                break;
        }
    }

    private void testBody(String eventType, String result) {
        switch (eventType) {
            case RH_NEW_ROLE_AVAILABLE:
                assertTrue(result.contains("Red Hat now provides a new role"));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertTrue(result.contains("Red Hat has updated the role"));
                assertTrue(result.contains("The role belongs to the platform default access group and"));
                assertTrue(result.contains("will be inherited by all users within your account."));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                assertTrue(result.contains("Red Hat has updated the role"));
                assertTrue(result.contains("The role does not belong to the platform default access group."));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case CUSTOM_ROLE_CREATED:
                assertTrue(result.contains("custom role"));
                assertTrue(result.contains("has been created by"));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case CUSTOM_ROLE_UPDATED:
                assertTrue(result.contains("has been updated by"));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains("Custom role"));
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                } else {
                    assertTrue(result.contains("A custom role"));
                }
                break;
            case CUSTOM_ROLE_DELETED:
                assertTrue(result.contains("Custom role"));
                assertTrue(result.contains("has been deleted by"));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS:
                assertTrue(result.contains("Red Hat added a role"));
                assertTrue(result.contains("to platform default access group."));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                assertTrue(result.contains("Red Hat removed a role"));
                assertTrue(result.contains("from platform default access group"));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                assertTrue(result.contains("Custom platform default access group has been updated by"));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case GROUP_CREATED:
                assertTrue(result.contains("A custom group"));
                assertTrue(result.contains("has been created by"));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case GROUP_UPDATED:
                assertTrue(result.contains("Custom group has been updated by"));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case GROUP_DELETED:
                assertTrue(result.contains("Custom group"));
                assertTrue(result.contains("has been deleted by"));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                assertTrue(result.contains("Platform default group is modified by"));
                assertTrue(result.contains("and the Red Hat will not be responsible for managing it from now on."));
                if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            default:
                break;
        }
    }


    private String generateFromTemplate(TemplateInstance templateInstance, Action action) {
        return templateInstance
            .data("action", action)
            .data("environment", environment)
            .data("user", Map.of("firstName", "Drift User", "lastName", "RHEL"))
            .render();
    }

    public void writeEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
            TestHelpers.writeEmailTemplate(result, fileName);
        }
    }
}
