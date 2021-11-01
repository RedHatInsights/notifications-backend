package com.redhat.cloud.notifications.demoCamelSender;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import java.util.HashMap;
import java.util.Map;

/**
 * Transformer to create a Map from the outcome of the actual component.
 */
public class ResultTransformer implements Processor {

    public void process(Exchange exchange) throws Exception {

        Message in = exchange.getIn();
        String oldBody = in.getBody(String.class);

        Object timeHeader = in.getHeader("timeIn");
        long timeIn;
        if (timeHeader != null) {
            timeIn = Long.parseLong((String) timeHeader); // TODO use header("kafka.TIMESTAMP") ?
        } else {
            timeIn = System.currentTimeMillis();
        }
        long timeDiff = System.currentTimeMillis() - timeIn;

        Map<String, Object> out = new HashMap<>();
        out.put("outcome", oldBody);
        boolean isFail = Boolean.parseBoolean((String) in.getHeader("outcome-fail", "false"));
        out.put("successful", !isFail);
        out.put("finishTime", System.currentTimeMillis());
        out.put("duration", timeDiff);
        Map<String, String> details = new HashMap<>();
        details.put("target", (String) in.getHeader("targetUrl"));
        details.put("type", (String) in.getHeader("Ce-type"));
        out.put("details", details);

        in.setBody(out);
    }

}
