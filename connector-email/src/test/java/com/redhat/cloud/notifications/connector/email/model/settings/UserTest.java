package com.redhat.cloud.notifications.connector.email.model.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    @Test
    void testEqualityBasedOnUsernameOnly() {
        User u1 = buildUser("alice", "a@example.com", true);
        User u2 = buildUser("alice", "different@example.com", false);
        assertEquals(u1, u2);
        assertEquals(u1.hashCode(), u2.hashCode());
    }

    @Test
    void testInequalityWithDifferentUsername() {
        User u1 = buildUser("alice", "same@example.com", true);
        User u2 = buildUser("bob", "same@example.com", true);
        assertNotEquals(u1, u2);
    }

    @Test
    void testEqualityWithSelf() {
        User u = buildUser("alice", "a@example.com", false);
        assertEquals(u, u);
    }

    @Test
    void testInequalityWithNull() {
        User u = buildUser("alice", "a@example.com", false);
        assertNotEquals(null, u);
    }

    @Test
    void testInequalityWithDifferentType() {
        User u = buildUser("alice", "a@example.com", false);
        assertNotEquals("alice", u);
    }

    @Test
    void testJsonDeserializationIgnoresUnknownFields() throws Exception {
        String json = "{\"username\":\"alice\",\"email\":\"alice@example.com\",\"admin\":true,\"unknown_field\":\"ignored\"}";
        User user = new ObjectMapper().readValue(json, User.class);
        assertEquals("alice", user.getUsername());
        assertEquals("alice@example.com", user.getEmail());
        assertTrue(user.isAdmin());
    }

    private static User buildUser(String username, String email, boolean admin) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setAdmin(admin);
        return u;
    }
}
