package com.redhat.cloud.notifications.demoCamelSender;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * We decode a CloudEvent, set the headers accordingly and put the CE payload as the new body
 */
public class CloudEventDecoder implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Message in = exchange.getIn();
        String body = in.getBody(String.class);
        JsonObject ceIn = (JsonObject) Jsoner.deserialize(body);
        for (String key : ceIn.keySet()) {
            if (key.equals("data")) {
                continue; // This is the data. We don't put it in the header
            }
            in.setHeader("Ce-" + key, ceIn.getString(key));
        }

        // Extract metadata, put it in headers and then delete from the body.
        JsonObject bodyObject = (JsonObject) Jsoner.deserialize(ceIn.getString("data"));
        JsonObject metaData = (JsonObject) bodyObject.get("notif-metadata");
        in.setHeader("metadata", metaData);
        JsonObject extras = (JsonObject) Jsoner.deserialize(metaData.getString("extras"));
        in.setHeader("extras", extras);

        bodyObject.remove("notif-metadata");

        in.setBody(bodyObject);
    }
}
