package com.redhat.cloud.notifications.recipients;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


@QuarkusTest
public class RecipientResolverTest {

    private static final String ACCOUNT_ID = "acc-1";

    @InjectMock
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    private static class TestRecipientSettings extends RecipientSettings {

        private final boolean onlyAdmins;
        private final boolean ignoreUserPreferences;
        private final UUID groupId;

        TestRecipientSettings(boolean onlyAdmins, boolean ignoreUserPreferences, UUID groupId) {
            this.onlyAdmins = onlyAdmins;
            this.ignoreUserPreferences = ignoreUserPreferences;
            this.groupId = groupId;
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
                eq(false)
        )).thenReturn(Uni.createFrom().item(List.of(
                user1, user2, user3, admin1, admin2
        )));

        when(rbacRecipientUsersProvider.getUsers(
                eq(ACCOUNT_ID),
                eq(true)
        )).thenReturn(Uni.createFrom().item(List.of(
                admin1, admin2
        )));

        when(rbacRecipientUsersProvider.getGroupUsers(
                eq(ACCOUNT_ID),
                eq(false),
                eq(group1)
        )).thenReturn(Uni.createFrom().item(List.of(
                user1, admin1
        )));

        when(rbacRecipientUsersProvider.getGroupUsers(
                eq(ACCOUNT_ID),
                eq(true),
                eq(group1)
        )).thenReturn(Uni.createFrom().item(List.of(
                admin1
        )));

        when(rbacRecipientUsersProvider.getGroupUsers(
                eq(ACCOUNT_ID),
                eq(false),
                eq(group2)
        )).thenReturn(Uni.createFrom().item(List.of(
                user2, admin2
        )));

        when(rbacRecipientUsersProvider.getGroupUsers(
                eq(ACCOUNT_ID),
                eq(true),
                eq(group2)
        )).thenReturn(Uni.createFrom().item(List.of(
                admin2
        )));

        // Default request, all subscribed users
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRecipientSettings(false, false, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user1, admin1
                ));
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(false)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // subscribed admin users
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRecipientSettings(true, false, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        admin1
                ));
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(true)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // users, ignoring preferences
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRecipientSettings(false, true, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user1, user2, user3, admin1, admin2
                ));
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(false)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // admins, ignoring preferences
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRecipientSettings(true, true, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        admin1, admin2
                ));
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(true)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all subscribed users & admins ignoring preferences
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRecipientSettings(false, false, null),
                        new TestRecipientSettings(true, true, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user1, admin1, admin2
                ));
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(true)
        );
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(false)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all users ignoring preferences & admins ignoring preferences (redundant, but possible)
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRecipientSettings(false, true, null),
                        new TestRecipientSettings(true, true, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user1, user2, user3, admin1, admin2
                ));
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(true)
        );
        verify(rbacRecipientUsersProvider, times(1)).getUsers(
                eq(ACCOUNT_ID),
                eq(false)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all subscribed users from group 1
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRecipientSettings(false, false, group1)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user1, admin1
                ));
        verify(rbacRecipientUsersProvider, times(1)).getGroupUsers(
                eq(ACCOUNT_ID),
                eq(false),
                eq(group1)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all subscribed admins from group 1
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRecipientSettings(true, false, group1)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        admin1
                ));
        verify(rbacRecipientUsersProvider, times(1)).getGroupUsers(
                eq(ACCOUNT_ID),
                eq(true),
                eq(group1)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all subscribed users from group 2
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRecipientSettings(false, false, group2)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of());
        verify(rbacRecipientUsersProvider, times(1)).getGroupUsers(
                eq(ACCOUNT_ID),
                eq(false),
                eq(group2)
        );
        verifyNoMoreInteractions(rbacRecipientUsersProvider);
        clearInvocations(rbacRecipientUsersProvider);

        // all users from group 2 (ignoring preferences)
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRecipientSettings(false, true, group2)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user2, admin2
                ));
        verify(rbacRecipientUsersProvider, times(1)).getGroupUsers(
                eq(ACCOUNT_ID),
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
