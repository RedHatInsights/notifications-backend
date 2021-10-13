package com.redhat.cloud.notifications.demoCamelSender;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;

/**
 * The main class that does the work setting up the Camel routes.
 * Entry point for messages is blow 'from(INCOMING_CHANNEL)
 * Upon success/failure a message is returned to the RETURN_CHANNEL
 * topic.
 */

/*
 * We need to register some classes for reflection here, so that
 * native compilation can work if desired.
 */
@RegisterForReflection(targets = {
    Exception.class,
    IOException.class
})
@ApplicationScoped
public class Main extends RouteBuilder {

    // Tha name of our component. Must be unique
    public static final String COMPONENT_NAME = "demo-log";
    // We receive our commands from here
    // The groupId is basically our type and must be different for each component type
    public static final String INCOMING_CHANNEL = "kafka:platform.notifications.toCamel?groupId=" + COMPONENT_NAME;
    // We send  our outcome on this one.
    public static final String RETURN_CHANNEL = "kafka:platform.notifications.fromCamel";

    /*
     * This method sets up the camel route and is started by the underlying
     * code when Quarkus-camel starts.
     */
    public void configure() {

        Processor resultTransformer = new ResultTransformer();
        Processor ceDecoder = new CloudEventDecoder();
        Processor ceEncoder = new CloudEventEncoder(COMPONENT_NAME);

        // If the sender fails, we mark the route as handled
        // and forward to the error handler
        // Setting handled to true ends the processing chain below
        // This is not needed with the log component, but left as example
        onException(IOException.class)
            .to("direct:error")
            .handled(true);

        // The error handler. We set the outcome to fail and then send to kafka
        from("direct:error")
                .setBody(constant("Fail"))
                .process(resultTransformer)
                .marshal().json()
                .log("Fail with ${body} and ${header.ce-id}")
                .process(ceEncoder)
                .to(RETURN_CHANNEL);

        // This is the component call that does the real work
        from("direct:log")
            .toD("log:my-" + COMPONENT_NAME + "ger?level=INFO")
            .setBody(constant("Success"));

        /*
         * Main processing entry point, receiving data from Kafka
         */
        from(INCOMING_CHANNEL)
                //            .log("Message received via Kafka : ${body}")
                .process(ceDecoder)

                // We check that this is our type.
                // Otherwise, we ignore the message there will be another component that takes care
                .filter().simple("${header.ce-type} != '" + COMPONENT_NAME + "'") // TODO ==
                .to("direct:doTheWork")
                .end();


        // This is doing some unmarshalling and then doing the work
        from("direct:doTheWork")

            .setHeader("timeIn", simpleF("%d", System.currentTimeMillis()))
            .setHeader("targetUrl", jsonpath("$.notif-metadata.url"))
            .setHeader("basicAuth", jsonpath("$.notif-metadata.basicAuth"))

            .errorHandler(
                    deadLetterChannel("direct:error"))
            // translate the json formatted string body into a Java class
            .unmarshal().json()

            // Now send it off to our component that does the "real sending"
            .to("direct:log")

            // Processing is done, now look at the output
            // and inform notifications
            .process(resultTransformer)
                // translate the inner stuff to json
                .marshal().json()
                .log("Success with ${body} and ${header.Ce-Id}")
                // encode as CloudEvent
                .process(ceEncoder)
                // marshall this again
                .marshal().json()
            .to(RETURN_CHANNEL);
    }
}
