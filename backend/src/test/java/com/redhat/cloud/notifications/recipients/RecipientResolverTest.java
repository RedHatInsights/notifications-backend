package com.redhat.cloud.notifications.recipients;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@QuarkusTest
public class RecipientResolverTest {

    private static final String ACCOUNT_ID = "acc-1";

    @InjectMock
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    private static class TestRequest extends RecipientResolverRequest {

        private final boolean onlyAdmins;
        private final boolean ignoreUserPreferences;
        private final UUID groupId;

        TestRequest(boolean onlyAdmins, boolean ignoreUserPreferences, UUID groupId) {
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
        Mockito.when(rbacRecipientUsersProvider.getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false)
        )).thenReturn(Uni.createFrom().item(List.of(
                user1, user2, user3, admin1, admin2
        )));

        Mockito.when(rbacRecipientUsersProvider.getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(true)
        )).thenReturn(Uni.createFrom().item(List.of(
                admin1, admin2
        )));

        Mockito.when(rbacRecipientUsersProvider.getGroupUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false),
                Mockito.eq(group1)
        )).thenReturn(Uni.createFrom().item(List.of(
                user1, admin1
        )));

        Mockito.when(rbacRecipientUsersProvider.getGroupUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(true),
                Mockito.eq(group1)
        )).thenReturn(Uni.createFrom().item(List.of(
                admin1
        )));

        Mockito.when(rbacRecipientUsersProvider.getGroupUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false),
                Mockito.eq(group2)
        )).thenReturn(Uni.createFrom().item(List.of(
                user2, admin2
        )));

        Mockito.when(rbacRecipientUsersProvider.getGroupUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(true),
                Mockito.eq(group2)
        )).thenReturn(Uni.createFrom().item(List.of(
                admin2
        )));

        // Default request, all subscribed users
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRequest(false, false, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user1, admin1
                ));
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false)
        );
        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
        Mockito.clearInvocations(rbacRecipientUsersProvider);

        // subscribed admin users
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRequest(true, false, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        admin1
                ));
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(true)
        );
        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
        Mockito.clearInvocations(rbacRecipientUsersProvider);

        // users, ignoring preferences
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRequest(false, true, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user1, user2, user3, admin1, admin2
                ));
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false)
        );
        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
        Mockito.clearInvocations(rbacRecipientUsersProvider);

        // admins, ignoring preferences
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRequest(true, true, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        admin1, admin2
                ));
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(true)
        );
        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
        Mockito.clearInvocations(rbacRecipientUsersProvider);

        // all subscribed users & admins ignoring preferences
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRequest(false, false, null),
                        new TestRequest(true, true, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user1, admin1, admin2
                ));
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(true)
        );
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false)
        );
        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
        Mockito.clearInvocations(rbacRecipientUsersProvider);

        // all users ignoring preferences & admins ignoring preferences (redundant, but possible)
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRequest(false, true, null),
                        new TestRequest(true, true, null)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user1, user2, user3, admin1, admin2
                ));
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(true)
        );
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false)
        );
        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
        Mockito.clearInvocations(rbacRecipientUsersProvider);

        // all subscribed users from group 1
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRequest(false, false, group1)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user1, admin1
                ));
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getGroupUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false),
                Mockito.eq(group1)
        );
        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
        Mockito.clearInvocations(rbacRecipientUsersProvider);

        // all subscribed admins from group 1
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRequest(true, false, group1)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        admin1
                ));
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getGroupUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(true),
                Mockito.eq(group1)
        );
        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
        Mockito.clearInvocations(rbacRecipientUsersProvider);

        // all subscribed users from group 2
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRequest(false, false, group2)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of());
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getGroupUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false),
                Mockito.eq(group2)
        );
        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
        Mockito.clearInvocations(rbacRecipientUsersProvider);

        // all users from group 2 (ignoring preferences)
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                        new TestRequest(false, true, group2)
                ),
                subscribedUsers
        ).subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(
                        user2, admin2
                ));
        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getGroupUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false),
                Mockito.eq(group2)
        );
        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
        Mockito.clearInvocations(rbacRecipientUsersProvider);

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
