package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.Endpoint;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.ConnectorReceiver.EGRESS_CHANNEL;
import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class IntegrationDisabledNotifier {

    public static final String CONSOLE_BUNDLE = "console";
    public static final String INTEGRATIONS_APP = "integrations";
    public static final String INTEGRATION_DISABLED_EVENT_TYPE = "integration-disabled";
    public static final String ERROR_TYPE_PROPERTY = "error_type";
    public static final String ERROR_DETAILS_PROPERTY = "error_details";
    public static final String ENDPOINT_ID_PROPERTY = "endpoint_id";
    public static final String ENDPOINT_NAME_PROPERTY = "endpoint_name";
    public static final String ENDPOINT_CATEGORY_PROPERTY = "endpoint_category";
    public static final String STATUS_CODE_PROPERTY = "status_code";
    public static final String ERRORS_COUNT_PROPERTY = "errors_count";

    @Channel(EGRESS_CHANNEL)
    Emitter<String> emitter;

    public void notify(Endpoint endpoint, HttpErrorType httpErrorType, Integer statusCode, int errorsCount) {
        Action action = buildIntegrationDisabledAction(endpoint, httpErrorType, statusCode, errorsCount);
        String encodedAction = Parser.encode(action);
        Message<String> message = KafkaMessageWithIdBuilder.build(encodedAction);
        emitter.send(message);
    }

    public static Action buildIntegrationDisabledAction(Endpoint endpoint, HttpErrorType httpErrorType, Integer statusCode, int errorsCount) {
        Context.ContextBuilderBase contextBuilder = new Context.ContextBuilder()
                .withAdditionalProperty(ERROR_TYPE_PROPERTY, httpErrorType.name())
                .withAdditionalProperty(ERROR_DETAILS_PROPERTY, httpErrorType.getMessage())
                .withAdditionalProperty(ENDPOINT_ID_PROPERTY, endpoint.getId())
                .withAdditionalProperty(ENDPOINT_NAME_PROPERTY, endpoint.getName())
                .withAdditionalProperty(ENDPOINT_CATEGORY_PROPERTY, getFrontendCategory(endpoint))
                .withAdditionalProperty(ERRORS_COUNT_PROPERTY, errorsCount);

        if (statusCode != null) {
            contextBuilder.withAdditionalProperty(STATUS_CODE_PROPERTY, statusCode);
        }

        Event event = new Event.EventBuilder()
                .withPayload(new Payload.PayloadBuilder().build())
                .build();

        Recipient recipients = new Recipient.RecipientBuilder()
                .withOnlyAdmins(true)
                .withIgnoreUserPreferences(true)
                .build();

        return new Action.ActionBuilder()
                .withId(UUID.randomUUID())
                .withBundle(CONSOLE_BUNDLE)
                .withApplication(INTEGRATIONS_APP)
                .withEventType(INTEGRATION_DISABLED_EVENT_TYPE)
                .withOrgId(endpoint.getOrgId())
                .withTimestamp(LocalDateTime.now(UTC))
                .withContext(contextBuilder.build())
                .withEvents(List.of(event))
                .withRecipients(List.of(recipients))
                .build();
    }

    /*
     * The following code is a problem for several reasons:
     * - the endpoints category is a frontend key that shouldn't appear in the backend code
     * - the endpoints subtype no longer makes sense, we should only have the type
     * TODO Let's improve that and fix problems ASAP!
     */
    private static String getFrontendCategory(Endpoint endpoint) {
        return switch (endpoint.getType()) {
            case ANSIBLE -> "Reporting";
            case CAMEL -> {
                yield switch (endpoint.getSubType()) {
                        case "google_chat", "slack", "teams" -> "Communications";
                        case "servicenow", "splunk" -> "Reporting";
                        default -> {
                            // The frontend will show the Cloud tab by default if we return an empty string.
                            yield "";
                        }
                    };
            }
            case WEBHOOK -> "Webhooks";
            default -> {
                // The frontend will show the Cloud tab by default if we return an empty string.
                yield "";
            }
        };
    }
}
