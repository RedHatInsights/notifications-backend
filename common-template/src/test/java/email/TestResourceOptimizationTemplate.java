package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestResourceOptimizationTemplate extends EmailTemplatesRendererHelper {

    public static final String JSON_RESOURCE_OPTIMIZATION_DEFAULT_AGGREGATION_CONTEXT = "{" +
        "   \"event_name\":\"New suggestion\"," +
        "   \"systems_with_suggestions\":134," +
        "   \"start_time\":\"2020-08-03T15:22:42.199046\"," +
        "   \"aggregated_data\":{" +
        "      \"systems_with_suggestions\":134," +
        "      \"systems_triggered\":2," +
        "      \"states\":[" +
        "         {" +
        "            \"system_count\":7," +
        "            \"state\":\"IDLING\"" +
        "         }," +
        "         {" +
        "            \"system_count\":1," +
        "            \"state\":\"UNKNOWN\"" +
        "         }," +
        "         {" +
        "            \"system_count\":4," +
        "            \"state\":\"UNDER_PRESSURE\"" +
        "         }" +
        "      ]" +
        "   }" +
        "}";

    @Override
    protected String getApp() {
        return "resource-optimization";
    }

    @Test
    public void testDailyDigestEmailBody() throws JsonProcessingException {
        String result = generateAggregatedEmailBody(JSON_RESOURCE_OPTIMIZATION_DEFAULT_AGGREGATION_CONTEXT);
        assertTrue(result.contains("Today, rules triggered on"));
        assertTrue(result.contains("IDLING"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
