package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.itservice.ITUserServiceWrapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@QuarkusTest
public class ITUserServiceIntegrationTest {

    @Inject
    ITUserServiceWrapper itUserService;

    @Test
    public void getAllUsersCache() {
        System.out.println(itUserService.getUserss("someAccountId", false).await().indefinitely());
    }
}
