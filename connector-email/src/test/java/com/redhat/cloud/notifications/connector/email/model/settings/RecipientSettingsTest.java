package com.redhat.cloud.notifications.connector.email.model.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipientSettingsTest {

    @Test
    void testConstructorAndGetters() {
        UUID groupId = UUID.randomUUID();
        RecipientSettings rs = new RecipientSettings(true, false, groupId, Set.of("alice"), Set.of("a@example.com"));
        assertTrue(rs.isAdminsOnly());
        assertFalse(rs.isIgnoreUserPreferences());
        assertEquals(groupId, rs.getGroupUUID());
        assertEquals(Set.of("alice"), rs.getUsers());
        assertEquals(Set.of("a@example.com"), rs.getEmails());
    }

    @Test
    void testEqualityWithSameFields() {
        UUID groupId = UUID.randomUUID();
        RecipientSettings r1 = new RecipientSettings(true, false, groupId, Set.of("alice"), Set.of("a@example.com"));
        RecipientSettings r2 = new RecipientSettings(true, false, groupId, Set.of("alice"), Set.of("a@example.com"));
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testInequalityWithDifferentAdminsOnly() {
        RecipientSettings r1 = new RecipientSettings(true, false, null, null, null);
        RecipientSettings r2 = new RecipientSettings(false, false, null, null, null);
        assertNotEquals(r1, r2);
    }

    @Test
    void testInequalityWithDifferentGroupUUID() {
        RecipientSettings r1 = new RecipientSettings(false, false, UUID.randomUUID(), null, null);
        RecipientSettings r2 = new RecipientSettings(false, false, UUID.randomUUID(), null, null);
        assertNotEquals(r1, r2);
    }

    @Test
    void testInequalityWithDifferentUsers() {
        RecipientSettings r1 = new RecipientSettings(false, false, null, Set.of("alice"), null);
        RecipientSettings r2 = new RecipientSettings(false, false, null, Set.of("bob"), null);
        assertNotEquals(r1, r2);
    }

    @Test
    void testEqualityWithSelf() {
        RecipientSettings rs = new RecipientSettings(false, true, null, null, null);
        assertEquals(rs, rs);
    }

    @Test
    void testInequalityWithNull() {
        RecipientSettings rs = new RecipientSettings(false, false, null, null, null);
        assertNotEquals(null, rs);
    }

    @Test
    void testDefaultConstructorProducesNullFields() {
        RecipientSettings rs = new RecipientSettings();
        assertFalse(rs.isAdminsOnly());
        assertFalse(rs.isIgnoreUserPreferences());
        assertNull(rs.getGroupUUID());
        assertNull(rs.getUsers());
        assertNull(rs.getEmails());
    }

    @Test
    void testJsonDeserializationWithSnakeCaseNames() throws Exception {
        UUID groupId = UUID.randomUUID();
        String json = String.format(
            "{\"admins_only\":true,\"ignore_user_preferences\":true,\"group_uuid\":\"%s\",\"users\":[\"alice\"],\"emails\":[\"a@example.com\"]}",
            groupId
        );
        RecipientSettings rs = new ObjectMapper().readValue(json, RecipientSettings.class);
        assertTrue(rs.isAdminsOnly());
        assertTrue(rs.isIgnoreUserPreferences());
        assertEquals(groupId, rs.getGroupUUID());
        assertEquals(Set.of("alice"), rs.getUsers());
        assertEquals(Set.of("a@example.com"), rs.getEmails());
    }
}
