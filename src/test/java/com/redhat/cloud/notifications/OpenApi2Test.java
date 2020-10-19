package com.redhat.cloud.notifications;

import com.reprezen.kaizen.oasparser.OpenApi3Parser;
import com.reprezen.kaizen.oasparser.model3.OpenApi3;
import com.reprezen.kaizen.oasparser.val.ValidationResults;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.Assert;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URL;

/**
 * Do some basic verification of the created
 * openapi.*.json files
 */
@QuarkusTest
@Tag("integration")
public class OpenApi2Test {

    // QuarkusTest will inject the host+port for us.
    @TestHTTPResource("/api/notifications/v1.0/openapi.json")
    URL nUrl;

    @TestHTTPResource("/api/notifications/v1/openapi.json")
    URL nUrl1;

    @TestHTTPResource("/api/integrations/v1.0/openapi.json")
    URL iUrl;

    @TestHTTPResource("/api/integrations/v1/openapi.json")
    URL iUrl1;

    @Test
    public void validateOpenApi() throws Exception {

        URL[] urls = {nUrl, iUrl, nUrl1, iUrl1};

        for (URL url : urls) {
            System.out.printf("Validating OpenAPI Model at %s\n", url);
            OpenApi3 model = new OpenApi3Parser().parse(url, true);
            if (!model.isValid()) {
                for (ValidationResults.ValidationItem item : model.getValidationItems()) {
                    System.err.println(item);
                }
                Assert.fail("OpenAPI spec is not valid");
            }
        }
    }
}
