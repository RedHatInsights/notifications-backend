package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.FILTERED_USERS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENTS_WITH_EMAIL_ERROR;

@ApplicationScoped
public class CloudEventHistoryBuilder extends OutgoingCloudEventBuilder {

    public static final String TOTAL_RECIPIENTS_KEY = "total_recipients";
    public static final String TOTAL_FAILURE_RECIPIENTS_KEY = "total_failure_recipients";

    @Override
    public void process(Exchange exchange) throws Exception {
        int totalRecipients = exchange.getProperty(FILTERED_USERS, Set.class).stream().mapToInt(i -> ((List<String>) i).size()).sum();
        Optional<Set<String>> recipientsWithError = Optional.ofNullable(exchange.getProperty(RECIPIENTS_WITH_EMAIL_ERROR, Set.class));
        exchange.setProperty(SUCCESSFUL, recipientsWithError.isEmpty() || recipientsWithError.get().size() == 0);
        super.process(exchange);

        Message in = exchange.getIn();
        JsonObject cloudEvent = new JsonObject(in.getBody(String.class));
        JsonObject data = new JsonObject(cloudEvent.getString("data"));
        data.getJsonObject("details").put(TOTAL_RECIPIENTS_KEY, totalRecipients);

        if (recipientsWithError.isPresent()) {
            data.getJsonObject("details").put(TOTAL_FAILURE_RECIPIENTS_KEY, recipientsWithError.get().size());
        }

        cloudEvent.put("data", data.encode());
        in.setBody(cloudEvent.encode());
    }
}
