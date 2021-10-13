package com.redhat.cloud.notifications.demoCamelSender;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;

import java.util.Iterator;
import java.util.Map;

/**
 * Encode the passed body in a CloudEvent.
 * By default we take the incoming "Ce-*" headers and
 * put them into the CE header
 */
public class CloudEventEncoder implements Processor {

    private String source;

    public CloudEventEncoder(String source) {
        this.source = source;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        Message in = exchange.getIn();
        JsonObject out = new JsonObject();

        Map<String, Object> headers = in.getHeaders();
        Iterator<String> iter = headers.keySet().iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            if (key.startsWith("Ce-")) {
                String tmp = key.substring(3);
                out.put(tmp,headers.get(key));
            }
        }
        // CloudEvent source1
        out.put("source", this.source);
        // TODO set the type
        // TODO set current time

        out.put("data", in.getBody(String.class));
        in.setBody(out);
    }
}
