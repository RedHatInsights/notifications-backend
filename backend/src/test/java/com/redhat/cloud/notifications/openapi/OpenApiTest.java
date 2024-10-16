package com.redhat.cloud.notifications.openapi;

import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.reprezen.kaizen.oasparser.OpenApi3Parser;
import com.reprezen.kaizen.oasparser.model3.OpenApi3;
import com.reprezen.kaizen.oasparser.model3.Operation;
import com.reprezen.kaizen.oasparser.model3.Path;
import com.reprezen.kaizen.oasparser.model3.SecurityParameter;
import com.reprezen.kaizen.oasparser.model3.SecurityRequirement;
import com.reprezen.kaizen.oasparser.val.ValidationResults;
import io.quarkus.logging.Log;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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

    @TestHTTPResource("/")
    URL baseUrl;

    String[] versions = {"v1", "v1.0", "v2", "v2.0"};
    String[] publicApis = {OApiFilter.INTEGRATIONS, OApiFilter.NOTIFICATIONS, OApiFilter.PRIVATE};

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
        List<URL> urls = buildApiUrls(true);

        for (URL url : urls) {
            OpenApi3 model = new OpenApi3Parser().parse(url, true);
            Log.infof("testing api %s", url);
            if (!model.isValid()) {
                for (ValidationResults.ValidationItem item : model.getValidationItems()) {
                    Log.error(item);
                }
                fail("OpenAPI spec is not valid");
            }

            SwaggerParseResult result = new OpenAPIParser().readLocation(url.toString(), null, null);
            if (result.getMessages().size() > 0) {
                result.getMessages().stream().forEach(message -> Log.error(message));
                fail("OpenAPI is not valid, check messages above");
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
        List<URL> urls = buildApiUrls(false);

        for (URL url: urls) {
            final OpenApi3 model = new OpenApi3Parser().parse(url, true);

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

        List<URL> urls = buildApiUrls(true);

        for (URL url: urls) {
            try (InputStream in = url.openStream()) {
                java.nio.file.Path path = Paths.get("./target/" + url.getPath());
                Files.createDirectories(path.getParent());
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private List<URL> buildApiUrls(boolean addInternalApi) throws Exception {
        List<URL> apiUrls = new ArrayList<>();
        for (String version: versions) {
            for (String api: publicApis) {
                apiUrls.add(baseUrl.toURI().resolve("api/%s/%s/openapi.json".formatted(api, version)).toURL());
            }
        }

        if (addInternalApi) {
            apiUrls.add(internalUrl);
        }

        return apiUrls;
    }
}
