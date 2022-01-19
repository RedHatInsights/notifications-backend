package com.redhat.cloud.notifications.recipients.itservice;

import com.redhat.cloud.notifications.recipients.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ITUserServiceWrapper {

    @Inject
    @RestClient
    ITUserService itUserService;

    public Uni<List<ITUserResponse>> getUsers(String accountId, boolean adminsOnly) {
        return itUserService.getUsers(new ITUserRequest(accountId, adminsOnly));
    }
}
