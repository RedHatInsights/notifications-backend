package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
public class RecipientResolverTest {

    private static final String ACCOUNT_ID = "acc-1";

    @InjectMock
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    @Test
    public void withPersonalizedEmailOff() {
        RecipientResolver recipientResolver = new RecipientResolver();
        recipientResolver.rbacUserQuery = false;
        recipientResolver.rbacRecipientUsersProvider = rbacRecipientUsersProvider;

        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
        endpoint.setEnabled(true);
        endpoint.setAccountId(ACCOUNT_ID);
        endpoint.setProperties(new EmailSubscriptionProperties());

        User user1 = new User();
        user1.setUsername("foo");
        User user2 = new User();
        user2.setUsername("bar");

        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(endpoint),
                Set.of("foo", "bar")
        )
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(Set.of(user1, user2));
    }

    @Test
    public void withPersonalizedEmailOn() {
        RecipientResolver recipientResolver = new RecipientResolver();
        recipientResolver.rbacUserQuery = true;
        recipientResolver.rbacRecipientUsersProvider = rbacRecipientUsersProvider;

        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
        endpoint.setEnabled(true);
        endpoint.setAccountId(ACCOUNT_ID);
        endpoint.setProperties(new EmailSubscriptionProperties());

        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(endpoint),
                Set.of("foo", "bar")
        )
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted();

        Mockito.verify(rbacRecipientUsersProvider, Mockito.times(1)).getUsers(
                Mockito.eq(ACCOUNT_ID),
                Mockito.eq(false)
        );

        Mockito.verifyNoMoreInteractions(rbacRecipientUsersProvider);
    }

}
