package com.redhat.cloud.notifications.demoCamelSender;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Encode the passed body in a CloudEvent, marshalled as Json
 */
public class CloudEventEncoder implements Processor {

    private static DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private String source;
    private String type;

    public CloudEventEncoder(String source, String type) {
        this.source = source;
        this.type = type;
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        Message in = exchange.getIn();
        JsonObject out = new JsonObject();

        // Save the id
        String id = (String) in.getHeader("ce-id");

        // Remove the incoming Ce-headers
        Map<String, Object> headers = in.getHeaders();
        Set<String> keys = new HashSet<>(headers.keySet());
        for (String key : keys) {
            if (key.startsWith("Ce-")) {
                headers.remove(key);
            }
        }

        // Set CloudEvent headers
        out.put("specversion", "1.0");
        out.put("source", this.source);
        out.put("type", this.type);
        out.put("time", df.format(new Date()));
        out.put("id", id);
        out.put("content-type", "application/json");

        // Attach payload
        out.put("data", in.getBody(String.class));

        // Marshall the CloudEvent to json
        String bodyAsJsonString = out.toJson();

        in.setBody(bodyAsJsonString);
    }
}
