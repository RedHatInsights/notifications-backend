package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.itservice.ITUserServiceWrapper;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

@QuarkusTest
public class ITUserServiceIntegrationTest {

    @Inject
    ITUserServiceWrapper itUserService;

    @Test
    public void shouldReturn5870NonAdminUsers() {
        final List<ITUserResponse> someAccountId = itUserService.getUsers("someAccountId", false).await().indefinitely();
        Assertions.assertEquals(5870, someAccountId.size());
    }

    @Test
    public void shouldReturn83AdminUsers() {
        final List<ITUserResponse> someAccountId = itUserService.getUsers("someAccountId", true).await().indefinitely();
        Assertions.assertEquals(83, someAccountId.size());
    }
}
