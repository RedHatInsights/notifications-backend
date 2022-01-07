package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.itservice.ITUserServiceWrapper;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@QuarkusTest
public class ITUserServiceIntegrationTest {

    @Inject
    ITUserServiceWrapper itUserService;

    /**
     * - returns 404 when LifecycleManager mockserver stuff is present
     * - returns 401 and others when changing parameters in application.properties
     */
    @Test
    public void getAllUsersCache() {
        final Page<RbacUser> someAccountId = itUserService.getUsers("someAccountId", false, 0, 0).await().indefinitely();
        System.out.println("BLA: " + someAccountId);
    }
}
