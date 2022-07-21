package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllOfTest {

    private AllOf testee;

    @BeforeEach
    void setUp() {
        this.testee = new AllOf();
    }

    @Test
    void shouldNotContainOrgIdWhenNotPresent() throws JsonProcessingException {
        testee.ebsAccountNumber = "someEbsAccountNumber";

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"ebsAccountNumber\":\"someEbsAccountNumber\",\"status\":null,\"permissionCode\":null}", result);
    }

    @Test
    void shouldContainOrgId() throws JsonProcessingException {
        testee.accountId = "someOrgId";

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"accountId\":\"someOrgId\",\"status\":null,\"permissionCode\":null}", result);
    }

    @Test
    void shouldNotContainEbsAccountNumberWhenItIsEmpty() throws JsonProcessingException {
        testee.accountId = "someOrgId";
        testee.ebsAccountNumber = "";

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"accountId\":\"someOrgId\",\"status\":null,\"permissionCode\":null}", result);
    }

    @Test
    void shouldNotContainOrgIdWhenItIsEmpty() throws JsonProcessingException {
        testee.accountId = "";
        testee.ebsAccountNumber = "someEbsAccountNumber";

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"ebsAccountNumber\":\"someEbsAccountNumber\",\"status\":null,\"permissionCode\":null}", result);
    }

    @Test
    void shouldNotContainEbsAccountNumberWhenItIsNull() throws JsonProcessingException {
        testee.accountId = "someOrgId";
        testee.ebsAccountNumber = null;

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"accountId\":\"someOrgId\",\"status\":null,\"permissionCode\":null}", result);
    }

    @Test
    void shouldNotContainOrgIdWhenItIsNull() throws JsonProcessingException {
        testee.accountId = null;
        testee.ebsAccountNumber = "someEbsAccountNumber";

        String result = new ObjectMapper().writeValueAsString(testee);

        assertEquals("{\"ebsAccountNumber\":\"someEbsAccountNumber\",\"status\":null,\"permissionCode\":null}", result);
    }
}
