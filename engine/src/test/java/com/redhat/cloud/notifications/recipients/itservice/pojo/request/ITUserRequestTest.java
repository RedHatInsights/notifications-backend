package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class ITUserRequestTest {

    private final ITUserRequest testee = new ITUserRequest("someAccountId", "someOrgId", false, true, 0, 101);

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
    void shouldContainAccountIdAsEbsAccountNumber() {
        assertEquals("someAccountId", testee.by.allOf.ebsAccountNumber);
    }

    @Test
    void shouldNotContainOrgIdByDefault() {
        assertNull(testee.by.allOf.accountId);
    }

    @Test
    void shouldContainOrgIdWhenOrgIdFlagIsTrue() {
        ITUserRequest testee = new ITUserRequest("someAccountId", "someOrgId", true, true, 0, 101);

        assertEquals("someOrgId", testee.by.allOf.accountId);
        assertNull(testee.by.allOf.ebsAccountNumber);
    }

    @Test
    void shouldFallBackIfOrgIdIsNullOrEmptyString() {
        String someAccountId = "someAccountId";
        ITUserRequest testeeNull = new ITUserRequest(someAccountId, null, true, true, 0, 101);
        ITUserRequest testeeEmptyString = new ITUserRequest(someAccountId, "", true, true, 0, 101);

        assertNull(testeeNull.by.allOf.accountId);
        assertEquals(someAccountId, testeeNull.by.allOf.ebsAccountNumber);

        assertNull(testeeEmptyString.by.allOf.accountId);
        assertEquals(someAccountId, testeeEmptyString.by.allOf.ebsAccountNumber);
    }
}
