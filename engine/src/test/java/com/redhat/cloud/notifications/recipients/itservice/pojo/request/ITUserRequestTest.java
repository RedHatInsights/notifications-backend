package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class ITUserRequestTest {

    private final ITUserRequest testee = new ITUserRequest("someOrgId", true, 0, 101);

    @Test
    void shouldSetPermissionCodeWhenAdminsOnly() {
        assertEquals("eq", testee.by.allOf.permissionCode.operand);
        assertEquals(RbacRecipientUsersProvider.ORG_ADMIN_PERMISSION, testee.by.allOf.permissionCode.value);
    }

    @Test
    void shouldSetAuthenticationsAndPersonalInformation() {
        assertEquals(List.of("authentications", "personal_information"), testee.include.allOf);
    }

    @Test
    void shouldSetPrimaryEmail() {
        assertEquals(List.of("primary_email"), testee.include.accountRelationships.get(0).allOf);
    }

    @Test
    void shouldHaveStatusEnabled() {
        assertEquals("enabled", testee.by.allOf.status);
    }

    @Test
    void shouldContainOrgIdAsAccountId() {
        assertEquals("someOrgId", testee.by.allOf.accountId);
        assertNull(testee.by.allOf.ebsAccountNumber);
    }
}
