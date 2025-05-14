package ms_teams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.networknt.schema.AnnotationKeyword;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.redhat.cloud.notifications.ingress.ParsingException;
import com.redhat.cloud.notifications.jackson.LocalDateTimeModule;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Set;

public class AdaptiveCardValidatorHelper {

    static final ObjectMapper objectMapper = new ObjectMapper();
    static final JsonSchema adaptiveCardJsonSchema;
    static final JsonSchema incomingWebhookJsonSchema;

    // Specific MS Adaptive Card schema
    static final String MS_ADAPTIVE_CARD_SCHEMA_PATH = "/templates/ms_teams/Common/adaptive-card-schema.json";

    // Global message schema, based on array of attachment
    static final String MS_TEAMS_WEBHOOK_SCHEMA_PATH = "/templates/ms_teams/Common/incoming-webhook-schema.json";

    static {
        // MS Adaptive Card schema uses specific "id" annotation while it should be labeled as $id according json specs
        JsonMetaSchema metaSchema = JsonMetaSchema.builder(JsonMetaSchema.getV6())
            .keyword(new AnnotationKeyword("id")).build();
        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V6,
            builder -> builder.metaSchema(metaSchema));

        adaptiveCardJsonSchema = schemaFactory.getSchema(
            TemplateService.class.getResourceAsStream(MS_ADAPTIVE_CARD_SCHEMA_PATH));

        incomingWebhookJsonSchema = schemaFactory.getSchema(
            TemplateService.class.getResourceAsStream(MS_TEAMS_WEBHOOK_SCHEMA_PATH));

        objectMapper.registerModule(new LocalDateTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Validates messages sent to MS teams and ensures all the values conform to the schema.
     * Message structure is documented here: https://learn.microsoft.com/en-us/connectors/teams/?tabs=text1%2Cdotnet#microsoft-teams-webhook
     * @param messageJson string of json encoded to be validated.
     */
    public static void validate(String messageJson) {

        try {
            JsonNode jsonNode = objectMapper.readTree(messageJson);

            // this validates the global message structure includes an array of attachments
            Set<ValidationMessage> errors = incomingWebhookJsonSchema.validate(jsonNode);
            if (!errors.isEmpty()) {
                throw new ParsingException(errors);
            }

            // this validates each attachment as adaptive card
            Iterator<JsonNode> attachementsIterator = jsonNode.get("attachments").elements();
            while (attachementsIterator.hasNext()) {
                JsonNode attachment = attachementsIterator.next();
                errors = adaptiveCardJsonSchema.validate(attachment.get("content"));
                if (!errors.isEmpty()) {
                    throw new ParsingException(errors);
                }
            }
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException("Unable to decode message", exception);
        }
    }
}
