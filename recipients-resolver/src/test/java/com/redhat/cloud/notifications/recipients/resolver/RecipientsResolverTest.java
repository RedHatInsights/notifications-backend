package com.redhat.cloud.notifications.recipients.resolver;

import com.redhat.cloud.notifications.recipients.model.RecipientSettings;
import com.redhat.cloud.notifications.recipients.model.User;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
public class RecipientsResolverTest {

    private static final String ORG_ID = "org-id-1";

    @Inject
    RecipientsResolver recipientsResolver;

    @InjectMock
    FetchUsersFromExternalServices fetchUsersFromExternalServices;

    @CacheName("find-recipients")
    Cache recipientsCache;

    User user1 = createUser("user1", false);
    User user2 = createUser("user2", false);
    User user3 = createUser("user3", false);
    User admin1 = createUser("admin1", true);
    User admin2 = createUser("admin2", true);

    UUID group1 = UUID.randomUUID();
    UUID group2 = UUID.randomUUID();

    @BeforeEach
    void beforeEach() {

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

        recipientsCache.invalidateAll().await().indefinitely();
    }

    @Test
    void testNotSubscribedByDefaultAndDefaultSettings() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, false, null, emptySet())),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin1, user1), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndAdminsOnly() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, false, null, emptySet())),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin1), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndIgnoreUserPreferences() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, true, null, emptySet())),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin1, admin2, user1, user2, user3), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndAdminsOnlyAndIgnoreUserPreferences() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, true, null, emptySet())),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin1, admin2), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndUsers() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, false, null,
                        Set.of(user1.getUsername(), user3.getUsername())
                )),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(user1), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndIgnoreUserPreferencesAndUsers() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, true, null,
                        Set.of(user1.getUsername(), user3.getUsername())
                )),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(user1, user3), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndAdminsOnlyAndUsers() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, false, null,
                        Set.of(user1.getUsername(), user3.getUsername(), admin1.getUsername(), admin2.getUsername())
                )),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin1), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndAdminsOnlyAndIgnoreUserPreferencesAndUsers() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, true, null,
                        Set.of(user1.getUsername(), user3.getUsername(), admin1.getUsername(), admin2.getUsername())
                )),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin1, admin2), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndSeveralSettings1() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(false, false, null, emptySet()),
                        new RecipientSettings(true, true, null, emptySet())
                ),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin1, admin2, user1), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndSeveralSettings2() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(false, true, null, emptySet()),
                        new RecipientSettings(true, true, null, emptySet())
                ),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin1, admin2, user1, user2, user3), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndGroup1() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, false, group1, emptySet())),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin1, user1), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(eq(ORG_ID), eq(false), eq(group1));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndAdminsOnlyAndGroup1() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, false, group1, emptySet())),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin1), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(eq(ORG_ID), eq(true), eq(group1));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testNotSubscribedByDefaultAndGroup2() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, false, group2, emptySet())),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertTrue(recipients.isEmpty());
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(eq(ORG_ID), eq(false), eq(group2));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    public void testNotSubscribedByDefaultAndIgnoreUserPreferencesAndGroup2() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, true, group2, emptySet())),
                Set.of("user1", "admin1"),
                emptySet(),
                false
        );
        assertEquals(Set.of(admin2, user2), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(eq(ORG_ID), eq(false), eq(group2));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndDefaultSettings() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, false, null, emptySet())),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(admin2, user2, user3), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndAdminsOnly() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, false, null, emptySet())),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(admin2), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndIgnoreUserPreferences() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, true, null, emptySet())),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(admin1, admin2, user1, user2, user3), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndAdminsOnlyAndIgnoreUserPreferences() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, true, null, emptySet())),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(admin1, admin2), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndUsers() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, false, null,
                        Set.of(user1.getUsername(), user3.getUsername())
                )),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(user3), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndIgnoreUserPreferencesAndUsers() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, true, null,
                        Set.of(user1.getUsername(), user3.getUsername())
                )),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(user1, user3), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndAdminsOnlyAndUsers() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, false, null,
                        Set.of(user1.getUsername(), user3.getUsername(), admin1.getUsername(), admin2.getUsername())
                )),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(admin2), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndAdminsOnlyAndIgnoreUserPreferencesAndUsers() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, true, null,
                        Set.of(user1.getUsername(), user3.getUsername(), admin1.getUsername(), admin2.getUsername())
                )),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(admin1, admin2), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndSeveralSettings1() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(false, false, null, emptySet()),
                        new RecipientSettings(true, true, null, emptySet())
                ),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(admin1, admin2, user2, user3), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndSeveralSettings2() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(
                        new RecipientSettings(false, true, null, emptySet()),
                        new RecipientSettings(true, true, null, emptySet())
                ),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(admin1, admin2, user1, user2, user3), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(true));
        verify(fetchUsersFromExternalServices, times(1)).getUsers(eq(ORG_ID), eq(false));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndGroup1() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, false, group1, emptySet())),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertTrue(recipients.isEmpty());
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(eq(ORG_ID), eq(false), eq(group1));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndAdminsOnlyAndGroup1() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(true, false, group1, emptySet())),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertTrue(recipients.isEmpty());
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(eq(ORG_ID), eq(true), eq(group1));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    public void testSubscribedByDefaultAndIgnoreUserPreferencesAndGroup1() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, true, group1, emptySet())),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(admin1, user1), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(eq(ORG_ID), eq(false), eq(group1));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    @Test
    void testSubscribedByDefaultAndGroup2() {
        Set<User> recipients = recipientsResolver.findRecipients(
                ORG_ID,
                Set.of(new RecipientSettings(false, false, group2, emptySet())),
                emptySet(),
                Set.of("user1", "admin1"),
                true
        );
        assertEquals(Set.of(admin2, user2), recipients);
        verify(fetchUsersFromExternalServices, times(1)).getGroupUsers(eq(ORG_ID), eq(false), eq(group2));
        verifyNoMoreInteractions(fetchUsersFromExternalServices);
    }

    public User createUser(String username, boolean isAdmin) {
        User user = new User();
        user.setUsername(username);
        user.setAdmin(isAdmin);
        return user;
    }
}
