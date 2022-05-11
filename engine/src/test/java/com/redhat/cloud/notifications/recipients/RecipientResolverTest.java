package com.redhat.cloud.notifications.recipients;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
public class RecipientResolverTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final String ORG_ID = "org-id-1";

    @InjectMock
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    private static class TestRecipientSettings extends RecipientSettings {

        private final boolean onlyAdmins;
        private final boolean ignoreUserPreferences;
        private final UUID groupId;
        private final Set<String> users;

        TestRecipientSettings(boolean onlyAdmins, boolean ignoreUserPreferences, UUID groupId, Set<String> users) {
            this.onlyAdmins = onlyAdmins;
            this.ignoreUserPreferences = ignoreUserPreferences;
            this.groupId = groupId;
            this.users = users;
        }

        @Override
        public boolean isOnlyAdmins() {
            return onlyAdmins;
        }

        @Override
        public boolean isIgnoreUserPreferences() {
            return ignoreUserPreferences;
        }

        @Override
        public UUID getGroupId() {
            return groupId;
        }

        @Override
        public Set<String> getUsers() {
            return users;
        }
    }

    @Test
    public void withPersonalizedEmailOn() {
        RecipientResolver recipientResolver = new RecipientResolver();
        recipientResolver.rbacRecipientUsersProvider = rbacRecipientUsersProvider;

        User user1 = createUser("user1", false);
        User user2 = createUser("user2", false);
        User user3 = createUser("user3", false);
        User admin1 = createUser("admin1", true);
        User admin2 = createUser("admin2", true);

        UUID group1 = UUID.randomUUID();
        UUID group2 = UUID.randomUUID();

        Set<String> subscribedUsers = Set.of("user1", "admin1");

        // Setting mocks
        when(rbacRecipientUsersProvider.getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false)
        )).thenReturn(List.of(
                user1, user2, user3, admin1, admin2
        ));

        when(rbacRecipientUsersProvider.getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(true)
        )).thenReturn(List.of(
                admin1, admin2
        ));

        when(rbacRecipientUsersProvider.getGroupUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false),
                eq(group1)
        )).thenReturn(List.of(
                user1, admin1
        ));

        when(rbacRecipientUsersProvider.getGroupUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(true),
                eq(group1)
        )).thenReturn(List.of(
                admin1
        ));

        when(rbacRecipientUsersProvider.getGroupUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false),
                eq(group2)
        )).thenReturn(List.of(
                user2, admin2
        ));

        when(rbacRecipientUsersProvider.getGroupUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(true),
                eq(group2)
        )).thenReturn(List.of(
                admin2
        ));

        // Default request, all subscribed users
        Set<User> users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(false, false, null, Set.of())
                ),
                subscribedUsers
        );
        assertEquals(Set.of(user1, admin1), users);
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // subscribed admin users
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(true, false, null, Set.of())
                ),
                subscribedUsers
        );
        assertEquals(Set.of(admin1), users);
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(true)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // users, ignoring preferences
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(false, true, null, Set.of())
                ),
                subscribedUsers
        );
        assertEquals(Set.of(user1, user2, user3, admin1, admin2), users);
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // admins, ignoring preferences
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(true, true, null, Set.of())
                ),
                subscribedUsers
        );
        assertEquals(Set.of(admin1, admin2), users);
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(true)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // Specifying users
        users = recipientResolver.recipientUsers(
            ACCOUNT_ID,
            ORG_ID,
            Set.of(
                    new TestRecipientSettings(false, false, null, Set.of(
                            user1.getUsername(), user3.getUsername()
                    ))
            ),
            subscribedUsers
        );
        assertEquals(Set.of(user1), users);

        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // Specifying users ignoring user preferences
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(false, true, null, Set.of(
                                user1.getUsername(), user3.getUsername()
                        ))
                ),
                subscribedUsers
        );
        assertEquals(Set.of(user1, user3), users);

        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // Specifying users and only admins
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(true, false, null, Set.of(
                                user1.getUsername(), user3.getUsername(), admin1.getUsername(), admin2.getUsername()
                        ))
                ),
                subscribedUsers
        );
        assertEquals(Set.of(admin1), users);

        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(true)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // Specifying users and only admins (ignoring user preferences)
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(true, true, null, Set.of(
                                user1.getUsername(), user3.getUsername(), admin1.getUsername(), admin2.getUsername()
                        ))
                ),
                subscribedUsers
        );
        assertEquals(Set.of(admin1, admin2), users);

        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(true)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all subscribed users & admins ignoring preferences
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(false, false, null, Set.of()),
                        new TestRecipientSettings(true, true, null, Set.of())
                ),
                subscribedUsers
        );
        assertEquals(Set.of(user1, admin1, admin2), users);
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(true)
        );
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all users ignoring preferences & admins ignoring preferences (redundant, but possible)
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(false, true, null, Set.of()),
                        new TestRecipientSettings(true, true, null, Set.of())
                ),
                subscribedUsers
        );
        assertEquals(Set.of(user1, user2, user3, admin1, admin2), users);
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(true)
        );
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all subscribed users from group 1
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(false, false, group1, Set.of())
                ),
                subscribedUsers
        );
        assertEquals(Set.of(user1, admin1), users);
        verify(rbacRecipientUsersProvider, times(1)).getGroupUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false),
                eq(group1)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all subscribed admins from group 1
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(true, false, group1, Set.of())
                ),
                subscribedUsers
        );
        assertEquals(Set.of(admin1), users);
        verify(rbacRecipientUsersProvider, times(1)).getGroupUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(true),
                eq(group1)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all subscribed users from group 2
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(false, false, group2, Set.of())
                ),
                subscribedUsers
        );
        assertEquals(Set.of(), users);
        verify(rbacRecipientUsersProvider, times(1)).getGroupUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false),
                eq(group2)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all users from group 2 (ignoring preferences)
        users = recipientResolver.recipientUsers(
                ACCOUNT_ID,
                ORG_ID,
                Set.of(
                        new TestRecipientSettings(false, true, group2, Set.of())
                ),
                subscribedUsers
        );
        assertEquals(Set.of(user2, admin2), users);
        verify(rbacRecipientUsersProvider, times(1)).getGroupUsers(
                eq(ACCOUNT_ID),
                eq(ORG_ID),
                eq(false),
                eq(group2)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

    }

    public User createUser(String username, boolean isAdmin) {
        User user = new User();
        user.setUsername(username);
        user.setAdmin(isAdmin);
        user.setActive(true);
        user.setEmail("user email");
        user.setFirstName("user firstname");
        user.setLastName("user lastname");
        return user;
    }

}
