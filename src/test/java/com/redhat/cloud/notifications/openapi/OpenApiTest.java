package com.redhat.cloud.notifications.openapi;

import com.redhat.cloud.notifications.TestConstants;
import com.reprezen.kaizen.oasparser.OpenApi3Parser;
import com.reprezen.kaizen.oasparser.model3.OpenApi3;
import com.reprezen.kaizen.oasparser.val.ValidationResults;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static io.restassured.RestAssured.given;
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

    @TestHTTPResource("/api/doesNotExist/v1.0/openapi.json")
    URL badUrl;

    @Test
    public void validateOpenApi() throws Exception {

        URL[] urls = {nUrl, iUrl, nUrl1, iUrl1};

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

        InputStream in = nUrl.openStream();
        Files.copy(in, Paths.get("./target/openapi.notifications.json"), StandardCopyOption.REPLACE_EXISTING);
        in.close();

        in = iUrl.openStream();
        Files.copy(in, Paths.get("./target/openapi.integrations.json"), StandardCopyOption.REPLACE_EXISTING);
        in.close();
    }
}
