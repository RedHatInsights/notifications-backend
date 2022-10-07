package com.redhat.cloud.notifications.openapi;

import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.reprezen.kaizen.oasparser.OpenApi3Parser;
import com.reprezen.kaizen.oasparser.model3.OpenApi3;
import com.reprezen.kaizen.oasparser.model3.Operation;
import com.reprezen.kaizen.oasparser.model3.Path;
import com.reprezen.kaizen.oasparser.model3.SecurityParameter;
import com.reprezen.kaizen.oasparser.model3.SecurityRequirement;
import com.reprezen.kaizen.oasparser.val.ValidationResults;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Do some basic verification of the created
 * openapi.*.json files
 */
@QuarkusTest
public class OpenApiTest {

    // QuarkusTest will inject the host+port for us.
    @TestHTTPResource(TestConstants.API_NOTIFICATIONS_V_1_0 + "/openapi.json")
    URL nUrl;

    @TestHTTPResource(TestConstants.API_NOTIFICATIONS_V_1 + "/openapi.json")
    URL nUrl1;

    @TestHTTPResource(TestConstants.API_INTEGRATIONS_V_1_0 + "/openapi.json")
    URL iUrl;

    @TestHTTPResource(TestConstants.API_INTEGRATIONS_V_1 + "/openapi.json")
    URL iUrl1;

    @TestHTTPResource("/api/" + OApiFilter.PRIVATE + "/v1.0/openapi.json")
    URL privateUrl;

    @TestHTTPResource("/" + OApiFilter.INTERNAL + "/openapi.json")
    URL internalUrl;

    @TestHTTPResource("/api/doesNotExist/v1.0/openapi.json")
    URL badUrl;

    /**
     * The name of the security scheme that smallrye will create for basic authentications. The default value is taken
     * from
     * <a href="https://quarkus.io/guides/openapi-swaggerui#quarkus-smallrye-openapi_quarkus.smallrye-openapi.security-scheme-name">the quarkus docs</a>.
     */
    @ConfigProperty(name = "quarkus.smallrye-openapi.security-scheme-name", defaultValue = "SecurityScheme")
    String securitySchemeName;

    @Test
    public void validateOpenApi() throws Exception {

        URL[] urls = {nUrl, iUrl, nUrl1, iUrl1, privateUrl, internalUrl};

        for (URL url : urls) {
            OpenApi3 model = new OpenApi3Parser().parse(url, true);
            if (!model.isValid()) {
                for (ValidationResults.ValidationItem item : model.getValidationItems()) {
                    System.err.println(item);
                }
                fail("OpenAPI spec is not valid");
            }
        }
    }

    /**
     * Tests that the default security schemes have all their scope definitions empty. Check the "@see" annotation below
     * and <a href="https://issues.redhat.com/browse/NOTIF-619">NOTIF-619</a> for more information.
     * @see OApiFilter#removeSecuritySchemeRoles(JsonObject)
     * @throws Exception if any unexpected exception is thrown.
     */
    @Test
    public void validateOpenApiNoDefaultSecuritySchemeScope() throws Exception {
        final OpenApi3 model = new OpenApi3Parser().parse(this.nUrl, true);

        // Loop through all the paths...
        for (final var pathEntry : model.getPaths().entrySet()) {
            final Path path = pathEntry.getValue();

            // ... through all the operations of the path...
            for (final var operationEntry : path.getOperations().entrySet()) {
                final Operation operation = operationEntry.getValue();

                // ... get the security requirements for the operation...
                final List<SecurityRequirement> securityRequirements = operation.getSecurityRequirements();
                for (final var securityRequirement : securityRequirements) {
                    // ... and make sure the default security scheme doesn't have any scopes in it.
                    final SecurityParameter require = securityRequirement.getRequirement(this.securitySchemeName);

                    assertTrue(require.getParameters().isEmpty(), "the security scheme's scope list should be empty. Got the following instead: " + require.getParameters());
                }
            }
        }
    }

    @Test
    void testIgnoreUnknownWhat() {
        given()
                .accept("application/json")
                .when()
                .get(badUrl)
                .then()
                .statusCode(401); // We do credential checks before path check.
    }

    @Test
    void exportOpenApiFile() throws Exception {

        try (InputStream in = nUrl.openStream()) {
            Files.copy(in, Paths.get("./target/openapi.notifications.json"), StandardCopyOption.REPLACE_EXISTING);
        }

        try (InputStream in = iUrl.openStream()) {
            Files.copy(in, Paths.get("./target/openapi.integrations.json"), StandardCopyOption.REPLACE_EXISTING);
        }

        try (InputStream in = privateUrl.openStream()) {
            Files.copy(in, Paths.get("./target/openapi.private.json"), StandardCopyOption.REPLACE_EXISTING);
        }

        try (InputStream in = internalUrl.openStream()) {
            Files.copy(in, Paths.get("./target/openapi.internal.json"), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
