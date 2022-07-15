package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertNull(testee.by.accountId);
    }

    @Test
    void shouldContainOrgIdWhenOrgIdFlagIsTrue() {
        ITUserRequest testee = new ITUserRequest("someAccountId", "someOrgId", true, true, 0, 101);

        assertEquals("someOrgId", testee.by.accountId);
        assertNull(testee.by.allOf.ebsAccountNumber);
    }

    @Test
    void shouldContainOrgId() throws JsonProcessingException {
        testee.by.accountId = "someOrgId";

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"by\":{\"accountId\":\"someOrgId\",\"allOf\":{\"ebsAccountNumber\":\"someAccountId\",\"status\":\"enabled\",\"permissionCode\":{\"value\":\"admin:org:all\",\"operand\":\"eq\"}},\"withPaging\":{\"firstResultIndex\":0,\"maxResults\":101}},\"include\":{\"allOf\":[\"authentications\",\"personal_information\"],\"accountRelationships\":[{\"allOf\":[\"primary_email\"],\"by\":{\"active\":true}}]}}", result);
    }

    @Test
    void shouldNotContainEbsAccountNumberWhenItIsEmpty() throws JsonProcessingException {
        testee.by.accountId = "someOrgId";
        testee.by.allOf.ebsAccountNumber = "";

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"by\":{\"accountId\":\"someOrgId\",\"allOf\":{\"status\":\"enabled\",\"permissionCode\":{\"value\":\"admin:org:all\",\"operand\":\"eq\"}},\"withPaging\":{\"firstResultIndex\":0,\"maxResults\":101}},\"include\":{\"allOf\":[\"authentications\",\"personal_information\"],\"accountRelationships\":[{\"allOf\":[\"primary_email\"],\"by\":{\"active\":true}}]}}", result);
    }

    @Test
    void shouldNotContainOrgIdWhenItIsEmpty() throws JsonProcessingException {
        testee.by.accountId = "";
        testee.by.allOf.ebsAccountNumber = "someEbsAccountNumber";

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"by\":{\"allOf\":{\"ebsAccountNumber\":\"someEbsAccountNumber\",\"status\":\"enabled\",\"permissionCode\":{\"value\":\"admin:org:all\",\"operand\":\"eq\"}},\"withPaging\":{\"firstResultIndex\":0,\"maxResults\":101}},\"include\":{\"allOf\":[\"authentications\",\"personal_information\"],\"accountRelationships\":[{\"allOf\":[\"primary_email\"],\"by\":{\"active\":true}}]}}", result);
    }

    @Test
    void shouldNotContainEbsAccountNumberWhenItIsNull() throws JsonProcessingException {
        testee.by.accountId = "someOrgId";
        testee.by.allOf.ebsAccountNumber = null;

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"by\":{\"accountId\":\"someOrgId\",\"allOf\":{\"status\":\"enabled\",\"permissionCode\":{\"value\":\"admin:org:all\",\"operand\":\"eq\"}},\"withPaging\":{\"firstResultIndex\":0,\"maxResults\":101}},\"include\":{\"allOf\":[\"authentications\",\"personal_information\"],\"accountRelationships\":[{\"allOf\":[\"primary_email\"],\"by\":{\"active\":true}}]}}", result);
    }

    @Test
    void shouldNotContainOrgIdWhenItIsNull() throws JsonProcessingException {
        testee.by.accountId = null;
        testee.by.allOf.ebsAccountNumber = "someEbsAccountNumber";

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"by\":{\"allOf\":{\"ebsAccountNumber\":\"someEbsAccountNumber\",\"status\":\"enabled\",\"permissionCode\":{\"value\":\"admin:org:all\",\"operand\":\"eq\"}},\"withPaging\":{\"firstResultIndex\":0,\"maxResults\":101}},\"include\":{\"allOf\":[\"authentications\",\"personal_information\"],\"accountRelationships\":[{\"allOf\":[\"primary_email\"],\"by\":{\"active\":true}}]}}", result);
    }
}
