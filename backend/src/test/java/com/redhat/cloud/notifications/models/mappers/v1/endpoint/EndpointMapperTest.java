package com.redhat.cloud.notifications.models.mappers.v1.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@QuarkusTest
public class EndpointMapperTest {
    @Inject
    EndpointMapper endpointMapper;

    @Inject
    ObjectMapper objectMapper;

    final JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    /**
     * Prior to the introduction of MapStruct and DTOs into the project, the {@link Endpoint}
     * class was being used as the exposed model to clients. This tests ensures that the introduction of DTOs and
     * mappers did not change the model exposed to the clients.
     *
     * @throws IOException if the JSON schema could not be loaded.
     */
    @Test
    void testEndpointMapper() throws IOException, URISyntaxException {
        // Prepare the endpoint we will convert to DTO to.
        final Endpoint endpoint = new Endpoint();

        endpoint.setId(UUID.randomUUID());
        endpoint.setName("endpoint name");
        endpoint.setDescription("endpoint description");
        endpoint.setEnabled(true);
        endpoint.setServerErrors(12);
        endpoint.setType(EndpointType.CAMEL);
        endpoint.setSubType("slack");
        endpoint.setCreated(LocalDateTime.now());

        final CamelProperties camelProperties = new CamelProperties();
        camelProperties.setDisableSslVerification(false);
        camelProperties.setUrl("https://redhat.com");
        camelProperties.setBasicAuthentication(new BasicAuthentication("username", "password"));
        camelProperties.setSecretToken("secret-token");

        final SystemSubscriptionProperties systemSubscriptionProperties = new SystemSubscriptionProperties();
        systemSubscriptionProperties.setGroupId(UUID.randomUUID());
        systemSubscriptionProperties.setIgnorePreferences(false);
        systemSubscriptionProperties.setOnlyAdmins(false);

        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setDisableSslVerification(false);
        webhookProperties.setMethod(HttpType.POST);
        webhookProperties.setUrl("https://redhat.com");
        webhookProperties.setBasicAuthentication(new BasicAuthentication("username", "password"));
        webhookProperties.setBearerAuthentication("bearer authentication");
        webhookProperties.setSecretToken("secret token");

        // Load the JSON Schema.
        final String schemaContents = Files.readString(Paths.get(Thread.currentThread().getContextClassLoader().getResource("json-schemas/v1/endpoint/endpoint-schema.json").toURI()));
        final JsonSchema endpointSchema = this.jsonSchemaFactory.getSchema(schemaContents);

        // Loop through the properties and generate the JSON for the DTO.
        for (final EndpointProperties properties : List.of(camelProperties, systemSubscriptionProperties, webhookProperties)) {
            endpoint.setProperties(properties);

            // Generate the DTO.
            final EndpointDTO endpointDTO = this.endpointMapper.toDTO(endpoint);

            // Turn it into JSON and validate it against the schema.
            final Set<ValidationMessage> validationMessages = endpointSchema.validate(this.objectMapper.valueToTree(endpointDTO));

            // Any errors are unexpected since the resulting JSON should be conforming to the defined schema.
            for (final ValidationMessage validationMessage : validationMessages) {
                Assertions.fail(String.format("unexpected validation failure \"%s\" with payload: %s", validationMessage, this.objectMapper.valueToTree(endpointDTO)));
            }
        }
    }
}
