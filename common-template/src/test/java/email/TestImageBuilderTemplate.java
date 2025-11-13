package email;

import io.quarkus.test.junit.QuarkusTest;

/**
 * TestImageBuilderTemplate for image-builder application.
 * Launch event type tests (launch-success, launch-failed) have been removed following
 * the decommissioning of the Launch/Provisioning service.
 */
@QuarkusTest
public class TestImageBuilderTemplate extends EmailTemplatesRendererHelper {

    // JSON aggregation context kept for compatibility with other tests that may reference it
    public static final String JSON_IMAGE_BUILDER_DEFAULT_AGGREGATION_CONTEXT = "{" +
        "   \"start_time\":null," +
        "   \"end_time\":null" +
        "}";

    @Override
    protected String getApp() {
        return "image-builder";
    }

    @Override
    protected String getAppDisplayName() {
        return "Images";
    }
}
