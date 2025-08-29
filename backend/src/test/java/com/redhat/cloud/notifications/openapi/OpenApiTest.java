package com.redhat.cloud.notifications.openapi;

import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.reprezen.kaizen.oasparser.OpenApi3Parser;
import com.reprezen.kaizen.oasparser.OpenApiParser;
import com.reprezen.kaizen.oasparser.model3.OpenApi3;
import com.reprezen.kaizen.oasparser.val.ValidationResults;
import io.quarkus.logging.Log;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
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

    private Optional<OpenApi3> getModelFromUrl(URL url) throws Exception {
        try {
            OpenApi3 model = new OpenApi3Parser().parse(url, true);
            return Optional.of(model);
        } catch (OpenApiParser.OpenApiParserException e) {
            if (!url.toString().contains("/private/v2")) {
                throw e;
            }
        }
        return Optional.empty();
    }

    @Test
    public void validateOpenApi() throws Exception {
        List<URL> urls = buildApiUrls(true);

        for (URL url : urls) {
            final Optional<OpenApi3> optModel = getModelFromUrl(url);
            if (optModel.isEmpty()) {
                continue;
            }
            final OpenApi3 model = optModel.get();

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
    void testPrivateV2DontExist() {
        given()
            .accept("application/json")
            .when()
            .get("/api/private/v2.0/openapi.json")
            .then()
            .statusCode(404);

        given()
            .accept("application/json")
            .when()
            .get("/api/private/v2/openapi.json")
            .then()
            .statusCode(404);
    }

    @Test
    void exportOpenApiFile() throws Exception {

        List<URL> urls = buildApiUrls(true);

        for (URL url: urls) {
            try (InputStream in = url.openStream()) {
                java.nio.file.Path path = Paths.get("./target/" + url.getPath());
                Files.createDirectories(path.getParent());
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                if (url.toString().contains("/private/v2")) {
                    continue;
                }
                throw e;
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
