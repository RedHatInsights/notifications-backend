package com.redhat.cloud.notifications.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ApplyDefaultsStrategy;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationResult;
import com.networknt.schema.ValidatorTypeCode;
import com.redhat.cloud.event.core.v1.RHELSystem;
import com.redhat.cloud.notifications.validator.LocalDateTimeValidator;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;

@ApplicationScoped
public class CloudEventParser {

    @Inject
    ObjectMapper objectMapper;

    JsonSchema jsonSchema;

    @PostConstruct
    void init() {
        jsonSchema = getJsonSchema();
    }

    public JsonNode fromJsonString(String cloudEventJson) {
        try {
            // Verify it's a valid Json
            JsonNode cloudEvent = objectMapper.readTree(cloudEventJson);
            ValidationResult result = jsonSchema.walk(cloudEvent, true);

            if (result.getValidationMessages().size() > 0) {
                throw new RuntimeException("Cloud event validation failed for: " + cloudEventJson + ". Failures: " + result.getValidationMessages().toString());
            }

            return cloudEvent;
        } catch (JsonProcessingException jme) {
            throw new RuntimeException("Cloud event parsing failed for: " + cloudEventJson, jme);
        }
    }

    private JsonSchema getJsonSchema() {
        SchemaValidatorsConfig schemaValidatorsConfig = new SchemaValidatorsConfig();
        schemaValidatorsConfig.setApplyDefaultsStrategy(new ApplyDefaultsStrategy(
                true,
                true,
                true
        ));

        try (InputStream jsonSchemaStream = RHELSystem.class.getResourceAsStream("/schemas/events/v1/events.json")) {
            JsonNode schema = objectMapper.readTree(jsonSchemaStream);

            return jsonSchemaFactory().getSchema(
                    schema,
                    schemaValidatorsConfig
            );
        } catch (IOException ioe) {
            throw new JsonSchemaException(ioe);
        }
    }

    private static JsonSchemaFactory jsonSchemaFactory() {
        String ID = "$id";

        JsonMetaSchema overrideDateTimeValidator = new JsonMetaSchema.Builder(JsonMetaSchema.getV7().getUri())
                .idKeyword(ID)
                .addKeywords(ValidatorTypeCode.getNonFormatKeywords(SpecVersion.VersionFlag.V7))
                .addFormats(JsonMetaSchema.COMMON_BUILTIN_FORMATS)
                .addFormat(new LocalDateTimeValidator())
                .build();

        return new JsonSchemaFactory.Builder().defaultMetaSchemaURI(overrideDateTimeValidator.getUri())
                .addMetaSchema(overrideDateTimeValidator)
                .build();

    }

}
