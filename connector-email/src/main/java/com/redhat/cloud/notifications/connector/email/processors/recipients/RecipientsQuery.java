package com.redhat.cloud.notifications.connector.email.processors.recipients;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import io.vertx.core.json.JsonObject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class RecipientsQuery {

    @NotBlank
    public String orgId;

    @NotNull
    public Set<RecipientSettings> recipientSettings;

    public Set<String> subscribers;

    public Set<String> unsubscribers;

    public boolean subscribedByDefault;

    public JsonObject authorizationCriteria;
}
