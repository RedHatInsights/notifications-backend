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
}
