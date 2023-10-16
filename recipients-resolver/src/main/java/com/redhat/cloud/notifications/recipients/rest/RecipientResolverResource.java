package com.redhat.cloud.notifications.recipients.rest;

import com.redhat.cloud.notifications.recipients.model.User;
import com.redhat.cloud.notifications.recipients.resolver.RecipientResolver;
import com.redhat.cloud.notifications.recipients.rest.pojo.Meta;
import com.redhat.cloud.notifications.recipients.rest.pojo.Page;
import com.redhat.cloud.notifications.recipients.rest.pojo.RecipientQuery;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import java.util.List;


import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/internal/recipient-resolver")
public class RecipientResolverResource {

    static final int LIMIT = 1000;

    @Inject
    RecipientResolver recipientsResolver;

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Page<User> getRecipients(@NotNull @Valid RecipientQuery resolverQuery) {
        List<User> userList = recipientsResolver.findRecipients(resolverQuery.getOrgId(), resolverQuery.getRecipientSettings(), resolverQuery.getSubscribers(), resolverQuery.isOptIn());

        long count = userList.size();
        Meta meta = new Meta();
        meta.setCount(count);
        int offset = resolverQuery.getOffset();

        Page<User> page = new Page<>();
        if (offset > userList.size()) {
            page.setData(List.of());
        } else if (offset + LIMIT > userList.size()) {
            page.setData(userList.subList(offset, userList.size()));
        } else {
            page.setData(userList.subList(offset, offset + LIMIT));
        }
        page.setMeta(meta);
        return page;
    }
}
