package com.redhat.cloud.notifications.recipients.resolver.itservice;

import com.redhat.cloud.notifications.recipients.model.User;
import com.redhat.cloud.notifications.recipients.resolver.FetchUsersFromExternalServices;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.AccountRelationship;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.Authentication;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.Email;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.PersonalInformation;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
public class ITUserServiceTest {

    @Inject
    FetchUsersFromExternalServices rbacRecipientUsersProvider;

    @InjectMock
    @RestClient
    ITUserService itUserService;

    @Test
    void shouldPickPrimaryEMailAsUsersEmail() {
        ITUserResponse itUserResponse = new ITUserResponse();

        final PersonalInformation personalInformation = new PersonalInformation();
        personalInformation.firstName = "myFirstname";
        personalInformation.lastNames = "myLastname";
        itUserResponse.personalInformation = personalInformation;

        Authentication authentication = new Authentication();
        authentication.principal = "myPrincipal";
        authentication.providerName = "myProviderName";
        itUserResponse.authentications = new ArrayList<>();
        itUserResponse.authentications.add(authentication);

        final AccountRelationship accountRelationship = new AccountRelationship();

        Email primaryEmail = new Email();
        primaryEmail.isPrimary = true;
        primaryEmail.address = "first_adress@trashmail.org";

        Email nonPrimaryEmail = new Email();
        nonPrimaryEmail.isPrimary = false;
        nonPrimaryEmail.address = "second_adress@trashmail.org";

        accountRelationship.emails = new ArrayList<>();
        accountRelationship.emails.add(nonPrimaryEmail);
        accountRelationship.emails.add(primaryEmail);
        itUserResponse.accountRelationships = new ArrayList<>();
        itUserResponse.accountRelationships.add(accountRelationship);
        itUserResponse.accountRelationships.get(0).permissions = List.of();
        List<ITUserResponse> itUserResponses = List.of(itUserResponse);

        when(itUserService.getUsers(any(ITUserRequest.class))).thenReturn(itUserResponses);

        final List<User> someAccountId = rbacRecipientUsersProvider.getUsers("someOrgId", true);
    }

    @Test
    void shouldMapUsersCorrectly() {
        final FetchUsersFromExternalServices mock = Mockito.mock(FetchUsersFromExternalServices.class);
        User mockedUser = createNonAdminMockedUser();
        List<User> mockedUsers = List.of(mockedUser);

        when(mock.getUsers(anyString(), anyBoolean())).thenReturn(mockedUsers);
        final List<User> users = mock.getUsers("someOrgId", false);

        final User user = users.get(0);
        assertEquals("userName", user.getUsername());
        assertFalse(user.isAdmin());
    }

    private User createNonAdminMockedUser() {
        User mockedUser = new User();
        mockedUser.setUsername("userName");
        mockedUser.setAdmin(false);
        return mockedUser;
    }
}
