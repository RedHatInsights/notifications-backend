package com.redhat.cloud.notifications.recipients.itservice;

import com.redhat.cloud.notifications.recipients.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.recipients.rbac.RbacUser;
import com.redhat.cloud.notifications.routers.models.Page;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class ITUserServiceWrapper {

    @Inject
    @RestClient
    ITUserService itUserService;

    public Uni<List<ITUserResponse>> getUserss(String accountId, boolean adminsOnly) {
        return itUserService.getUserss(new ITUserRequest(adminsOnly));
    }
}
