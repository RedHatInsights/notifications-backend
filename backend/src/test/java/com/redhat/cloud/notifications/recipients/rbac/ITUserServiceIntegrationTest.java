package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.itservice.ITUserServiceWrapper;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

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
        List<ITUserResponse> itUserResponse = itUserService.getUserss("someAccountId", false, 0, 0);
        System.out.println("BLA: " + itUserResponse);
    }
}
