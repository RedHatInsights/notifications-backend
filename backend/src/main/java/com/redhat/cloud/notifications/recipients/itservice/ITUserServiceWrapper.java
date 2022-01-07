package com.redhat.cloud.notifications.recipients.itservice;

import com.redhat.cloud.notifications.recipients.rbac.RbacUser;
import com.redhat.cloud.notifications.routers.models.Page;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class ITUserServiceWrapper {

    @Inject
    @RestClient
    ITUserService itUserService;

    public Uni<Page<RbacUser>> getUsers(String accountId, boolean adminsOnly, int i, Integer rbacElementsPerPage) {
        Path fileName = Path.of("src/main/resources/users.json");
        final String s;
        try {
            s = Files.readString(fileName);
            return itUserService.getUsers(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
