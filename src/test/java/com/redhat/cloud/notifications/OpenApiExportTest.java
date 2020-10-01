package com.redhat.cloud.notifications;

import com.reprezen.kaizen.oasparser.OpenApi3Parser;
import com.reprezen.kaizen.oasparser.model3.OpenApi3;
import com.reprezen.kaizen.oasparser.val.ValidationResults;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@QuarkusTest
public class OpenApiExportTest {

    // QuarkusTest will inject the host+port for us.
    @TestHTTPResource("/openapi.json")
    URL url;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = "/api";
    }

    private static final String TARGET_OPENAPI = "./target/openapi.json";

    @Test
    public void validateAndExternaliseOpenApi() throws Exception {
        System.out.printf("Validating OpenAPI Model at %s\n", url);
        OpenApi3 model = new OpenApi3Parser().parse(url, true);
        if (!model.isValid()) {
            for (ValidationResults.ValidationItem item : model.getValidationItems()) {
                System.err.println(item);
            }
            Assert.fail("OpenAPI spec is not valid");
        }

        // Now that the OpenAPI file has been validated, save a copy to the filesystem
        // This file is going to be uploaded in a regular CI build to know the API state
        // for a given build.
        InputStream in = url.openStream();
        Files.copy(in, Paths.get(TARGET_OPENAPI), StandardCopyOption.REPLACE_EXISTING);

    }
}
