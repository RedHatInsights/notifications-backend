package com.redhat.cloud.notifications.recipients.itservice;

import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.AccountRelationship;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.Authentication;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.Email;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.PersonalInformation;
import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class ITUserServiceTest {

    @Test
    void shouldPickPrimaryEMailAsUsersEmail() {
        final ITUserServiceWrapper itUserServiceWrapper = Mockito.mock(ITUserServiceWrapper.class);

        ITUserResponse itUserResponse = new ITUserResponse();

        final PersonalInformation personalInformation = new PersonalInformation();
        personalInformation.firstName = "myFirstname";
        personalInformation.lastNames = "myLastname";
        itUserResponse.personalInformation = personalInformation;

        Authentication authentication = new Authentication();
        authentication.principal = "myPrincilal";
        authentication.providerName = "myProviderName";
        itUserResponse.authentications = new LinkedList<>();
        itUserResponse.authentications.add(authentication);

        final AccountRelationship accountRelationship = new AccountRelationship();

        Email primaryEmail = new Email();
        primaryEmail.isPrimary = true;
        primaryEmail.address = "first_adress@trashmail.org";

        Email nonPrimaryEmail = new Email();
        nonPrimaryEmail.isPrimary = false;
        nonPrimaryEmail.address = "second_adress@trashmail.org";

        accountRelationship.emails = new LinkedList<>();
        accountRelationship.emails.add(nonPrimaryEmail);
        accountRelationship.emails.add(primaryEmail);
        itUserResponse.accountRelationships = new LinkedList<>();
        itUserResponse.accountRelationships.add(accountRelationship);
        List<ITUserResponse> itUserResponses = List.of(itUserResponse);

        RbacRecipientUsersProvider rbacRecipientUsersProvider = new RbacRecipientUsersProvider(itUserServiceWrapper, new SimpleMeterRegistry());
        Mockito.when(itUserServiceWrapper.getUsers(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(itUserResponses);
        // TODO NOTIF-381 Remove this when the recipients retrieval from RBAC is removed.
        rbacRecipientUsersProvider.retrieveUsersFromIt = true;
        final List<User> someAccountId = rbacRecipientUsersProvider.getUsers("someAccountId", true);
        assertTrue(someAccountId.get(0).isActive());
        assertTrue(someAccountId.get(0).isAdmin());

        assertEquals(someAccountId.get(0).getEmail(), "first_adress@trashmail.org");
    }

    @Test
    void shouldMapUsersCorrectly() {
        final RbacRecipientUsersProvider mock = Mockito.mock(RbacRecipientUsersProvider.class);
        User mockedUser = createNonAdminMockedUser();
        List<User> mockedUsers = List.of(mockedUser);

        Mockito.when(mock.getUsers(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(mockedUsers);
        final List<User> users = mock.getUsers("someAccountId", false);

        final User user = users.get(0);
        assertEquals("firstName", user.getFirstName());
        assertEquals("lastName", user.getLastName());
        assertEquals("userName", user.getUsername());
        assertEquals("email@trashmail.xyz", user.getEmail());
        assertTrue(user.isActive());
        assertFalse(user.isAdmin());
    }

    private User createNonAdminMockedUser() {
        User mockedUser = new User();
        mockedUser.setActive(true);
        mockedUser.setLastName("lastName");
        mockedUser.setFirstName("firstName");
        mockedUser.setUsername("userName");
        mockedUser.setEmail("email@trashmail.xyz");
        mockedUser.setAdmin(false);
        mockedUser.setActive(true);
        return mockedUser;
    }
}
