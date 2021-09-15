package com.redhat.cloud.notifications.processors.email;

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
    public void withPersonalizedEmailOn() {
        RecipientResolver recipientResolver = new RecipientResolver();
        recipientResolver.rbacRecipientUsersProvider = rbacRecipientUsersProvider;

        RecipientResolverRequest  request = RecipientResolverRequest.builder().build();

        recipientResolver.recipientUsers(
                ACCOUNT_ID,
                Set.of(request),
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
