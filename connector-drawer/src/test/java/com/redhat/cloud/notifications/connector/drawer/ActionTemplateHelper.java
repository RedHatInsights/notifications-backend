package com.redhat.cloud.notifications.connector.drawer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class ActionTemplateHelper {

    public static final String actionAsJson = "{" +
        "   \"account_id\":null," +
        "   \"application\":\"policies\"," +
        "   \"bundle\":\"rhel\"," +
        "   \"context\":{" +
        "      \"foo\":\"im foo\"," +
        "      \"bar\":{" +
        "         \"baz\":\"im baz\"" +
        "      }" +
        "   }," +
        "   \"event_type\":\"triggered\"," +
        "   \"events\":[" +
        "      {" +
        "         \"metadata\":{" +
        "            " +
        "         }," +
        "         \"payload\":{" +
        "            " +
        "         }" +
        "      }" +
        "   ]," +
        "   \"org_id\":\"123456\"," +
        "   \"timestamp\":\"2022-08-24T13:30\"," +
        "   \"source\":{" +
        "      \"application\":{" +
        "         \"display_name\":\"The best app in the life\"" +
        "      }," +
        "      \"bundle\":{" +
        "         \"display_name\":\"A bundle\"" +
        "      }," +
        "      \"event_type\":{" +
        "         \"display_name\":\"Policies will take care of the rules\"" +
        "      }" +
        "   }" +
        "}";

    public static Map<String, Object> jsonActionToMap(String actionAsJson) {
        try {
            return (new ObjectMapper()).readValue(actionAsJson, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Drawer notification data transformation failed", e);
        }
    }
}
