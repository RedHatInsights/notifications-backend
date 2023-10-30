package com.redhat.cloud.notifications.connector.email.processors.it;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import com.redhat.cloud.notifications.connector.email.processors.it.pojo.response.AccountRelationship;
import com.redhat.cloud.notifications.connector.email.processors.it.pojo.response.Email;
import com.redhat.cloud.notifications.connector.email.processors.it.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.connector.email.processors.it.pojo.response.Permission;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.email.processors.it.ITConstants.ORG_ADMIN_PERMISSION;

@ApplicationScoped
public class ITResponseProcessor implements Processor {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Processes the response from the IT service. Grabs the users from the
     * response.
     * @param exchange the exchange of the pipeline.
     * @throws JsonProcessingException if the incoming payload cannot be read
     * into the {@link ITUserResponse} model.
     */
    @Override
    public void process(final Exchange exchange) throws JsonProcessingException {
        final String body = exchange.getMessage().getBody(String.class);
        final List<ITUserResponse> itUserResponses = Arrays.asList(this.objectMapper.readValue(body, ITUserResponse[].class));

        // Get the list of users we will fill with the extracted users
        // from IT's response.
        final Set<User> users = exchange.getProperty(ExchangeProperty.USERS, Set.class);

        for (final ITUserResponse itUserResponse : itUserResponses) {
            users.add(toUser(itUserResponse));
        }

        final int count = itUserResponses.size();
        exchange.setProperty(ExchangeProperty.ELEMENTS_COUNT, itUserResponses.size());

        // Update the offset in case we need to fetch the next page.
        final int limit = exchange.getProperty(ExchangeProperty.LIMIT, Integer.class);
        if (count == limit) {
            final int offset = exchange.getProperty(ExchangeProperty.OFFSET, Integer.class);
            exchange.setProperty(ExchangeProperty.OFFSET, offset + limit);
        }
    }

    private static User toUser(ITUserResponse itUserResponse) {

        User user = new User();
        user.setId(itUserResponse.id);
        user.setUsername(itUserResponse.authentications.get(0).principal);

        for (Email email : itUserResponse.accountRelationships.get(0).emails) {
            if (email != null && email.isPrimary != null && email.isPrimary) {
                user.setEmail(email.address);
            }
        }

        if (itUserResponse.accountRelationships != null) {
            for (AccountRelationship accountRelationship : itUserResponse.accountRelationships) {
                if (accountRelationship.permissions != null) {
                    for (Permission permission : accountRelationship.permissions) {
                        if (ORG_ADMIN_PERMISSION.equals(permission.permissionCode)) {
                            user.setAdmin(true);
                        }
                    }
                }
            }
        }

        return user;
    }
}
