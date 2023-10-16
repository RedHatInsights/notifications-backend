package com.redhat.cloud.notifications.recipients.resolver;

import com.redhat.cloud.notifications.recipients.model.RecipientSettings;
import com.redhat.cloud.notifications.recipients.model.User;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
public class RecipientResolverTest {

    private static final String ORG_ID = "org-id-1";

    @InjectMock
    FetchUsersFromExternalServices fetchUsersFromExternalServices;

    RecipientResolver recipientResolver = new RecipientResolver();

    User user1 = createUser("user1", false);
    User user2 = createUser("user2", false);
    User user3 = createUser("user3", false);
    User admin1 = createUser("admin1", true);
    User admin2 = createUser("admin2", true);

    UUID group1 = UUID.randomUUID();
    UUID group2 = UUID.randomUUID();

    Set<String> subscribedUsers = Set.of("user1", "admin1");

    @BeforeEach
    void beforeEach() {
        recipientResolver.fetchingUsers = fetchUsersFromExternalServices;

        // Setting mocks
        when(fetchUsersFromExternalServices.getUsers(
            eq(ORG_ID),
            eq(false)
        )).thenReturn(List.of(
            user1, user2, user3, admin1, admin2
        ));

        when(fetchUsersFromExternalServices.getUsers(
            eq(ORG_ID),
            eq(true)
        )).thenReturn(List.of(
            admin1, admin2
        ));

        when(fetchUsersFromExternalServices.getGroupUsers(
            eq(ORG_ID),
            eq(false),
            eq(group1)
        )).thenReturn(List.of(
            user1, admin1
        ));

        when(fetchUsersFromExternalServices.getGroupUsers(
            eq(ORG_ID),
            eq(true),
            eq(group1)
        )).thenReturn(List.of(
            admin1
        ));

        when(fetchUsersFromExternalServices.getGroupUsers(
            eq(ORG_ID),
            eq(false),
            eq(group2)
        )).thenReturn(List.of(
            user2, admin2
        ));

        when(fetchUsersFromExternalServices.getGroupUsers(
            eq(ORG_ID),
            eq(true),
            eq(group2)
        )).thenReturn(List.of(
            admin2
        ));
    }

    @Test
    public void withPersonalizedEmailOn() {
        boolean isOptIn = true;
        // Default request, all subscribed users
        List<User> users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, false, null, Set.of())),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(admin1, user1), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // subscribed admin users
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, false, null, Set.of())),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(admin1), users);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(true)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // users, ignoring preferences
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, true, null, Set.of())),
                subscribedUsers,
                isOptIn
        );

        assertEquals(List.of(admin1, admin2, user1, user2, user3), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // admins, ignoring preferences
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, true, null, Set.of())),
                subscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin1, admin2), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(true)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // Specifying users
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(false, false, null, Set.of(
                    user1.getUsername(), user3.getUsername()
                ))
            ),
            subscribedUsers,
            isOptIn
        );
        assertEquals(List.of(user1), users);

        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // Specifying users ignoring user preferences
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(false, true, null, Set.of(
                                user1.getUsername(), user3.getUsername()
                        ))
                ),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(user1, user3), sortListByUsername(users));

        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // Specifying users and only admins
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(true, false, null, Set.of(
                                user1.getUsername(), user3.getUsername(), admin1.getUsername(), admin2.getUsername()
                        ))
                ),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(admin1), users);

        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(true)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // Specifying users and only admins (ignoring user preferences)
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(true, true, null, Set.of(
                                user1.getUsername(), user3.getUsername(), admin1.getUsername(), admin2.getUsername()
                        ))
                ),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(admin1, admin2), sortListByUsername(users));

        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(true)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // all subscribed users & admins ignoring preferences
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(false, false, null, Set.of()),
                        new RecipientSettings(true, true, null, Set.of())
                ),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(admin1, admin2, user1), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(true)
        );
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // all users ignoring preferences & admins ignoring preferences (redundant, but possible)
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(false, true, null, Set.of()),
                        new RecipientSettings(true, true, null, Set.of())
                ),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(admin1, admin2, user1, user2, user3), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(true)
        );
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
                eq(ORG_ID),
                eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // all subscribed users from group 1
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(false, false, group1, Set.of())
                ),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(admin1, user1), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(
                eq(ORG_ID),
                eq(false),
                eq(group1)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // all subscribed admins from group 1
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(true, false, group1, Set.of())
                ),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(admin1), users);
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(
                eq(ORG_ID),
                eq(true),
                eq(group1)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // all subscribed users from group 2
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(false, false, group2, Set.of())
                ),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(), users);
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(
                eq(ORG_ID),
                eq(false),
                eq(group2)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // all users from group 2 (ignoring preferences)
        users = recipientResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(false, true, group2, Set.of())
                ),
                subscribedUsers,
                isOptIn
        );
        assertEquals(List.of(admin2, user2), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(
                eq(ORG_ID),
                eq(false),
                eq(group2)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

    }

    @Test
    public void withPersonalizedEmailOnOptOut() {
        boolean isOptIn = false;

        Set<String> unsubscribedUsers = subscribedUsers;

        // Default request, all subscribed users
        List<User> users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(false, false, null, Set.of())
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin2, user2, user3), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // subscribed admin users
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(true, false, null, Set.of())
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin2), users);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(true)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // users, ignoring preferences
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(false, true, null, Set.of())
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin1, admin2, user1, user2, user3), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // admins, ignoring preferences
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(true, true, null, Set.of())
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin1, admin2), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(true)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // Specifying users
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(false, false, null, Set.of(
                    user1.getUsername(), user3.getUsername()
                ))
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(user3), users);

        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // Specifying users ignoring user preferences
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(false, true, null, Set.of(
                    user1.getUsername(), user3.getUsername()
                ))
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(user1, user3), sortListByUsername(users));

        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // Specifying users and only admins
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(true, false, null, Set.of(
                    user1.getUsername(), user3.getUsername(), admin1.getUsername(), admin2.getUsername()
                ))
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin2), users);

        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(true)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // Specifying users and only admins (ignoring user preferences)
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(true, true, null, Set.of(
                    user1.getUsername(), user3.getUsername(), admin1.getUsername(), admin2.getUsername()
                ))
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin1, admin2), sortListByUsername(users));

        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(true)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // all subscribed users & admins ignoring preferences
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(false, false, null, Set.of()),
                new RecipientSettings(true, true, null, Set.of())
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin1, admin2, user2, user3), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(true)
        );
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // all users ignoring preferences & admins ignoring preferences (redundant, but possible)
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(false, true, null, Set.of()),
                new RecipientSettings(true, true, null, Set.of())
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin1, admin2, user1, user2, user3), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(true)
        );
        verify(fetchUsersFromExternalServices, times(1)).getUsers(
            eq(ORG_ID),
            eq(false)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // none users from group 1
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(false, false, group1, Set.of())
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(), users);
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(
            eq(ORG_ID),
            eq(false),
            eq(group1)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // none users from group 1
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(true, false, group1, Set.of())
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(), users);
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(
            eq(ORG_ID),
            eq(true),
            eq(group1)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // all subscribed users from group 2
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(false, false, group2, Set.of())
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin2, user2), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(
            eq(ORG_ID),
            eq(false),
            eq(group2)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

        // all users from group 2 (ignoring preferences)
        users = recipientResolver.findRecipients(
            ORG_ID,
            Set.of(
                new RecipientSettings(false, true, group2, Set.of())
            ),
            unsubscribedUsers,
            isOptIn
        );
        assertEquals(List.of(admin2, user2), sortListByUsername(users));
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(
            eq(ORG_ID),
            eq(false),
            eq(group2)
        );
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
        clearInvocations(fetchUsersFromExternalServices);

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

    private List<User> sortListByUsername(List<User> users) {
        return users.stream().sorted(Comparator.comparing(User::getUsername)).collect(Collectors.toList());
    }

}
