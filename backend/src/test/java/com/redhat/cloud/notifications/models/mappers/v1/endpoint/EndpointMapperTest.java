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
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.models.PagerDutySeverity;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointStatusDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointTypeDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.CamelPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.WebhookPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.secrets.BasicAuthenticationDTO;
import io.quarkus.test.junit.QuarkusTest;
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

// TODO add PagerDuty
@QuarkusTest
public class EndpointMapperTest {
    private static final String ENDPOINT_CAMEL_CREATE_JSON = "json-schemas/v1/endpoint/implementations/create/endpoint-camel.json";
    private static final String ENDPOINT_WEBHOOK_CREATE_JSON = "json-schemas/v1/endpoint/implementations/create/endpoint-webhook.json";
    private static final String SCHEMA_PATH_ENDPOINT_CREATE = "json-schemas/v1/endpoint/endpoint-schema-create.json";
    private static final String SCHEMA_PATH_ENDPOINT_READ = "json-schemas/v1/endpoint/endpoint-schema-read.json";

    final EndpointMapper endpointMapper;
    /**
     * Schema to use when we want to validate the "endpoint create" payloads.
     */
    final JsonSchema schemaEndpointCreate;
    /**
     * Schema to use when we want to validate the "endpoint read" payloads.
     */
    final JsonSchema schemaEndpointRead;
    final ObjectMapper objectMapper;

    /**
     * Create the EndpointMapperTest class.
     * @param endpointMapper the endpoint mapper to be injected.
     * @param objectMapper the object mapper to be injected.
     * @throws IOException
     * @throws URISyntaxException
     */
    public EndpointMapperTest(
        EndpointMapper endpointMapper,
        ObjectMapper objectMapper
    ) throws IOException, URISyntaxException {
        this.endpointMapper = endpointMapper;
        this.objectMapper = objectMapper;

        // Load the JSON Schema.
        final JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

        // Create the corresponding schemas.
        final String schemaContentsEndpointCreate = this.readStringFromClasspathResource(SCHEMA_PATH_ENDPOINT_CREATE);
        this.schemaEndpointCreate = jsonSchemaFactory.getSchema(schemaContentsEndpointCreate);

        final String schemaContentsEndpointRead = this.readStringFromClasspathResource(SCHEMA_PATH_ENDPOINT_READ);
        this.schemaEndpointRead = jsonSchemaFactory.getSchema(schemaContentsEndpointRead);
    }

    /**
     * Prior to the introduction of MapStruct and DTOs into the project, the {@link Endpoint}
     * class was being used as the exposed model to clients. This tests ensures that the introduction of DTOs and
     * mappers did not change the model exposed to the clients.
     * @throws IOException if the JSON schema's contents could not be read.
     * @throws URISyntaxException if the JSON schema's path is wrong.
     */
    @Test
    void testEndpointMapperToDTO() throws IOException, URISyntaxException {
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
        endpoint.setUpdated(LocalDateTime.now());

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

        final PagerDutyProperties pagerDutyProperties = new PagerDutyProperties();
        pagerDutyProperties.setSeverity(PagerDutySeverity.ERROR);
        pagerDutyProperties.setSecretToken("secret token");

        // Loop through the properties and generate the JSON for the DTO.
        for (final EndpointProperties properties : List.of(camelProperties, systemSubscriptionProperties, webhookProperties)) {
            endpoint.setProperties(properties);

            // Generate the DTO.
            final EndpointDTO endpointDTO = this.endpointMapper.toDTO(endpoint);

            // Turn it into JSON and validate it against the schema.
            final Set<ValidationMessage> validationMessages = this.schemaEndpointRead.validate(this.objectMapper.valueToTree(endpointDTO));

            // Any errors are unexpected since the resulting JSON should be conforming to the defined schema.
            this.assertNoSchemaValidationErrors(validationMessages);
        }
    }

    /**
     * Test that a payload which conforms to the endpoint schema is properly
     * deserialized into a DTO, and that then that DTO gets properly mapped to
     * an internal entity.
     * @throws IOException if the JSON file cannot be read.
     * @throws URISyntaxException if the path to the JSON file is incorrect.
     */
    @Test
    void testEndpointMapperCamelToDTOToEntity() throws IOException, URISyntaxException {
        final String endpointCamelJSON = this.readStringFromClasspathResource(ENDPOINT_CAMEL_CREATE_JSON);

        // Assert that the JSON file complies with the defined schema.
        final Set<ValidationMessage> validationMessages = this.schemaEndpointCreate.validate(this.objectMapper.readTree(endpointCamelJSON));
        this.assertNoSchemaValidationErrors(validationMessages);

        // Attempt reading the JSON into a DTO, which should succeed.
        final EndpointDTO dto = this.objectMapper.readValue(endpointCamelJSON, EndpointDTO.class);

        // Assert that the DTO was properly built.
        Assertions.assertEquals("Camel endpoint", dto.getName(), "the name of the endpoint was not properly deserialized");
        Assertions.assertEquals("A JSON structure which represents a camel endpoint", dto.getDescription(), "the description of the endpoint was not properly deserialized");
        Assertions.assertTrue(dto.getEnabled(), "the \"enabled\" status of the endpoint was not properly deserialized");
        Assertions.assertEquals(EndpointStatusDTO.READY, dto.getStatus(), "the status of the endpoint was not properly deserialized");
        Assertions.assertEquals(23, dto.getServerErrors(), "the number of server errors of the endpoint were not properly deserialized");
        Assertions.assertEquals("slack", dto.getSubType(), "the subtype of the endpoint was not properly deserialized");
        Assertions.assertEquals(EndpointTypeDTO.CAMEL, dto.getType(), "the type of the endpoint was not properly deserialized");

        // Assert that the endpoint's properties were correctly deserialized.
        Assertions.assertInstanceOf(CamelPropertiesDTO.class, dto.getProperties(), "the properties of the camel endpoint were not properly deserialized as camel properties");
        final CamelPropertiesDTO camelPropertiesDTO = (CamelPropertiesDTO) dto.getProperties();

        Assertions.assertTrue(camelPropertiesDTO.getDisableSslVerification(), "the \"disable SSL verification\" flag was not properly deserialized");
        Assertions.assertEquals("my-slack-channel", camelPropertiesDTO.getExtras().get("channel"), "the \"channel\" property from the \"extras\" was not properly deserialized");
        Assertions.assertEquals("https://redhat.com", camelPropertiesDTO.getUrl(), "the url was not properly deserialized");

        final BasicAuthenticationDTO basicAuthenticationDTO = camelPropertiesDTO.getBasicAuthentication();
        Assertions.assertNotNull(basicAuthenticationDTO, "the basic authentication object was not properly deserialized");
        Assertions.assertEquals("camel-user", basicAuthenticationDTO.getUsername(), "the username of the basic authentication object was not properly deserialized");
        Assertions.assertEquals("camel-pass", basicAuthenticationDTO.getPassword(), "the password of the basic authentication object was not properly deserialized");

        Assertions.assertEquals("c4m3l-up3r-s3cr3t-t0k3n", camelPropertiesDTO.getSecretToken(), "the secret token was not properly deserialized");

        final Endpoint entity = this.endpointMapper.toEntity(dto);

        // Assert that the entity was properly mapped.
        Assertions.assertEquals("Camel endpoint", entity.getName(), "the name of the endpoint was not properly mapped");
        Assertions.assertEquals("A JSON structure which represents a camel endpoint", entity.getDescription(), "the description of the endpoint was not properly mapped");
        Assertions.assertTrue(entity.isEnabled(), "the \"enabled\" status of the endpoint was not properly mapped");
        Assertions.assertEquals(EndpointStatus.READY, entity.getStatus(), "the status of the endpoint was not properly mapped");
        Assertions.assertEquals(23, entity.getServerErrors(), "the number of server errors of the endpoint were not properly mapped");
        Assertions.assertEquals("slack", entity.getSubType(), "the subtype of the endpoint was not properly mapped");
        Assertions.assertEquals(EndpointType.CAMEL, entity.getType(), "the type of the endpoint was not properly mapped");

        // Assert that the endpoint's properties were correctly mapped.
        Assertions.assertInstanceOf(CamelProperties.class, entity.getProperties(), "the properties of the camel endpoint were not properly mapped as camel properties");
        final CamelProperties camelProperties = (CamelProperties) entity.getProperties();

        Assertions.assertTrue(camelProperties.getDisableSslVerification(), "the \"disable SSL verification\" flag was not properly mapped");
        Assertions.assertEquals("my-slack-channel", camelProperties.getExtras().get("channel"), "the \"channel\" property from the \"extras\" was not properly mapped");
        Assertions.assertEquals("https://redhat.com", camelProperties.getUrl(), "the url was not properly mapped");

        final BasicAuthentication basicAuthentication = camelProperties.getBasicAuthentication();
        Assertions.assertNotNull(basicAuthentication, "the basic authentication object was not properly mapped");
        Assertions.assertEquals("camel-user", basicAuthentication.getUsername(), "the username of the basic authentication object was not properly mapped");
        Assertions.assertEquals("camel-pass", basicAuthentication.getPassword(), "the password of the basic authentication object was not properly mapped");

        Assertions.assertEquals("c4m3l-up3r-s3cr3t-t0k3n", camelProperties.getSecretToken(), "the secret token was not properly mapped");
    }

    /**
     * Test that a payload which conforms to the endpoint schema is properly
     * deserialized into a DTO, and that then that DTO gets properly mapped to
     * an internal entity.
     * @throws IOException if the JSON file cannot be read.
     * @throws URISyntaxException if the path to the JSON file is incorrect.
     */
    @Test
    void testEndpointMapperWebhookToDTOToEntity() throws IOException, URISyntaxException {
        final String endpointWebhookJson = this.readStringFromClasspathResource(ENDPOINT_WEBHOOK_CREATE_JSON);

        // Assert that the JSON file complies with the defined schema.
        final Set<ValidationMessage> validationMessages = this.schemaEndpointCreate.validate(this.objectMapper.readTree(endpointWebhookJson));
        this.assertNoSchemaValidationErrors(validationMessages);

        // Attempt reading the JSON into a DTO, which should succeed.
        final EndpointDTO dto = this.objectMapper.readValue(endpointWebhookJson, EndpointDTO.class);

        // Assert that the DTO was properly built.
        Assertions.assertEquals("Webhook endpoint", dto.getName(), "the name of the endpoint was not properly deserialized");
        Assertions.assertEquals("A JSON structure which represents a webhook endpoint", dto.getDescription(), "the description of the endpoint was not properly deserialized");
        Assertions.assertTrue(dto.getEnabled(), "the \"enabled\" status of the endpoint was not properly deserialized");
        Assertions.assertEquals(EndpointStatusDTO.READY, dto.getStatus(), "the status of the endpoint was not properly deserialized");
        Assertions.assertEquals(12, dto.getServerErrors(), "the number of server errors of the endpoint were not properly deserialized");
        Assertions.assertNull(dto.getSubType(), "the subtype of the endpoint was not properly deserialized");
        Assertions.assertEquals(EndpointTypeDTO.WEBHOOK, dto.getType(), "the type of the endpoint was not properly deserialized");

        // Assert that the endpoint's properties were correctly deserialized.
        Assertions.assertInstanceOf(WebhookPropertiesDTO.class, dto.getProperties(), "the properties of the webhook endpoint were not properly deserialized as webhook properties");
        final WebhookPropertiesDTO webhookPropertiesDTO = (WebhookPropertiesDTO) dto.getProperties();

        Assertions.assertTrue(webhookPropertiesDTO.getDisableSslVerification(), "the \"disable SSL verification\" flag was not properly deserialized");
        Assertions.assertEquals(HttpType.POST, webhookPropertiesDTO.getMethod(), "the method property was not properly deserialized");
        Assertions.assertEquals("https://redhat.com", webhookPropertiesDTO.getUrl(), "the url was not properly deserialized");

        final BasicAuthenticationDTO basicAuthenticationDTO = webhookPropertiesDTO.getBasicAuthentication();
        Assertions.assertNotNull(basicAuthenticationDTO, "the basic authentication object was not properly deserialized");
        Assertions.assertEquals("webhook-user", basicAuthenticationDTO.getUsername(), "the username of the basic authentication object was not properly deserialized");
        Assertions.assertEquals("webhook-pass", basicAuthenticationDTO.getPassword(), "the password of the basic authentication object was not properly deserialized");

        Assertions.assertEquals("w3bh00k-bearer-t0k3n", webhookPropertiesDTO.getBearerAuthentication(), "the bearer authentication was not properly deserialized");
        Assertions.assertEquals("w3bh00k-sup3r-s3cr3t-t0k3n", webhookPropertiesDTO.getSecretToken(), "the secret token was not properly deserialized");

        final Endpoint entity = this.endpointMapper.toEntity(dto);

        // Assert that the entity was properly mapped.
        Assertions.assertEquals("Webhook endpoint", entity.getName(), "the name of the endpoint was not properly mapped");
        Assertions.assertEquals("A JSON structure which represents a webhook endpoint", entity.getDescription(), "the description of the endpoint was not properly mapped");
        Assertions.assertTrue(entity.isEnabled(), "the \"enabled\" status of the endpoint was not properly mapped");
        Assertions.assertEquals(EndpointStatus.READY, entity.getStatus(), "the status of the endpoint was not properly mapped");
        Assertions.assertEquals(12, entity.getServerErrors(), "the number of server errors of the endpoint were not properly mapped");
        Assertions.assertNull(entity.getSubType(), "the subtype of the endpoint was not properly mapped");
        Assertions.assertEquals(EndpointType.WEBHOOK, entity.getType(), "the type of the endpoint was not properly mapped");

        // Assert that the endpoint's properties were correctly mapped.
        Assertions.assertInstanceOf(WebhookProperties.class, entity.getProperties(), "the properties of the webhook endpoint were not properly mapped as webhook properties");
        final WebhookProperties webhookProperties = (WebhookProperties) entity.getProperties();

        Assertions.assertTrue(webhookProperties.getDisableSslVerification(), "the \"disable SSL verification\" flag was not properly mapped");
        Assertions.assertEquals(HttpType.POST, webhookPropertiesDTO.getMethod(), "the method property was not properly mapped");
        Assertions.assertEquals("https://redhat.com", webhookProperties.getUrl(), "the url was not properly mapped");

        final BasicAuthentication basicAuthentication = webhookProperties.getBasicAuthentication();
        Assertions.assertNotNull(basicAuthentication, "the basic authentication object was not properly mapped");
        Assertions.assertEquals("webhook-user", basicAuthentication.getUsername(), "the username of the basic authentication object was not properly mapped");
        Assertions.assertEquals("webhook-pass", basicAuthentication.getPassword(), "the password of the basic authentication object was not properly mapped");

        Assertions.assertEquals("w3bh00k-bearer-t0k3n", webhookProperties.getBearerAuthentication(), "the bearer authentication was not properly mapped");
        Assertions.assertEquals("w3bh00k-sup3r-s3cr3t-t0k3n", webhookProperties.getSecretToken(), "the secret token was not properly mapped");
    }


    /**
     * Test that a payload which conforms to the endpoint schema is properly
     * deserialized into a DTO, and that then that DTO gets properly mapped to
     * an internal entity.
     * @throws IOException if the JSON file cannot be read.
     * @throws URISyntaxException if the path to the JSON file is incorrect.
     */
    @Test
    void testEndpointMapperPagerDutyToDTOToEntity() throws IOException, URISyntaxException {
        // TODO implement pagerduty endpoint mapper test
    }

    private void assertNoSchemaValidationErrors(final Set<ValidationMessage> validationMessages) {
        // Any errors are unexpected since the resulting JSON should be conforming to the defined schema.
        for (final ValidationMessage validationMessage : validationMessages) {
            Assertions.fail(String.format("unexpected validation failure \"%s\"", validationMessage));
        }
    }

    /**
     * Reads the contents of a classpath resource as a string.
     * @param resourcePath the path of the resource.
     * @return the string contents of the resource.
     * @throws IOException if the contents could not be read.
     * @throws URISyntaxException if the resource's path is wrong.
     */
    private String readStringFromClasspathResource(final String resourcePath) throws IOException, URISyntaxException {
        return Files.readString(
            Paths.get(
                Thread.currentThread()
                    .getContextClassLoader()
                    .getResource(resourcePath)
                    .toURI()
            )
        );
    }
}
