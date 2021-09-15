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

@QuarkusTest
public class RecipientResolverTest {

    private static final String ACCOUNT_ID = "acc-1";

    @InjectMock
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    @Test
    public void withPersonalizedEmailOn() {
        RecipientResolver recipientResolver = new RecipientResolver();
        recipientResolver.rbacRecipientUsersProvider = rbacRecipientUsersProvider;

        User user1 = createUser("user1", false);
        User user2 = createUser("user2", false);
        User user3 = createUser("user3", false);
        User admin1 = createUser("admin1", true);
        User admin2 = createUser("admin2", true);

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

        // Default request, all subscribed users
        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(
                    RecipientResolverRequest.builder().build()
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
                        RecipientResolverRequest.builder().onlyAdmins(true).build()
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
                        RecipientResolverRequest.builder().ignoreUserPreferences(true).build()
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
                        RecipientResolverRequest.builder().onlyAdmins(true).ignoreUserPreferences(true).build()
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
                        RecipientResolverRequest.builder().onlyAdmins(false).ignoreUserPreferences(false).build(),
                        RecipientResolverRequest.builder().onlyAdmins(true).ignoreUserPreferences(true).build()
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
                        RecipientResolverRequest.builder().onlyAdmins(false).ignoreUserPreferences(true).build(),
                        RecipientResolverRequest.builder().onlyAdmins(true).ignoreUserPreferences(true).build()
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
