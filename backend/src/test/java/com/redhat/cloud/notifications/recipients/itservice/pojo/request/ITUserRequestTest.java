package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ITUserRequestTest {

    private final ITUserRequest testee = new ITUserRequest(true);

    @Test
    void shouldSetPermissionCodeWhenAdminsOnly() {
        assertEquals("eq", testee.by.allOf.permissionCode.operand);
        assertEquals("admin:org:all", testee.by.allOf.permissionCode.value);
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
}