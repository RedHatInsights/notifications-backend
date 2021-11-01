package com.redhat.cloud.notifications.demoCamelSender;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;

/**
 * The main class that does the work setting up the Camel routes. Entry point for messages is below
 * 'from(INCOMING_CHANNEL)' Upon success/failure a message is returned to the RETURN_CHANNEL topic.
 */

/*
 * We need to register some classes for reflection here, so that native compilation can work if desired.
 */
@RegisterForReflection(targets = { Exception.class, IOException.class })
@ApplicationScoped
public class Main extends RouteBuilder {

    // The name of our component. Must be unique
    public static final String COMPONENT_NAME = "demo-log";
    // We receive our commands from here
    // The groupId is basically our type and must be different for each component type
    public static final String INCOMING_CHANNEL = "kafka:platform.notifications.tocamel?groupId=" + COMPONENT_NAME;
    // We send our outcome on this one.
    public static final String RETURN_CHANNEL = "kafka:platform.notifications.fromcamel";
    // The (CloudEvent) type for the return channel
    public static final String RETURN_TYPE = "com.redhat.cloud.notifications.history";

    /*
     * This method sets up the camel route and is started by the underlying code when Quarkus-camel starts.
     */
    public void configure() {

        Processor resultTransformer = new ResultTransformer();
        Processor ceDecoder = new CloudEventDecoder();
        Processor ceEncoder = new CloudEventEncoder(COMPONENT_NAME, RETURN_TYPE);

        // If the sender fails, we mark the route as handled
        // and forward to the error handler
        // Setting handled to true ends the processing chain below
        // This is not needed with the log component, but left as example
        onException(IOException.class).to("direct:error").handled(true);

        // The error handler. We set the outcome to fail and then send to kafka
        from("direct:error").setBody(simple("${exception.message}")).setHeader("outcome-fail", simple("true"))
                .process(resultTransformer).marshal().json()
                .log("Fail with for id ${header.ce-id} : ${exception.message}").process(ceEncoder).to(RETURN_CHANNEL);

        // This is the component call that does the real work
        from("direct:log").toD("log:${header.extras[channel]}?level=INFO").setBody(constant("Success"));

        /*
         * Main processing entry point, receiving data from Kafka
         */
        from(INCOMING_CHANNEL).log("Message received via Kafka : ${body}")
                // Decode the CloudEvent
                .process(ceDecoder)

                // We check that this is our type.
                // Otherwise, we ignore the message there will be another component that takes care
                .filter()
                .simple("${header.ce-type} == 'com.redhat.console.notification.toCamel." + COMPONENT_NAME + "'")
                .to("direct:doTheWork").end();

        // This is doing some unmarshalling and then doing the work
        from("direct:doTheWork")

                .setHeader("targetUrl", simple("${headers.metadata[url]}"))
                .setHeader("timeIn", simpleF("%d", System.currentTimeMillis()))
                .errorHandler(deadLetterChannel("direct:error"))

                // If random is set in metadata, then try to randomly evaluate a non-existing header
                // to simulate a failure
                // Headers.extras is a map, so we use headers.extras[mode] to extract the value for key 'mode'
                .filter().simple("${headers.extras[mode]} == 'random'").filter().simple("${random(0,7)} < 2")
                .setHeader("does-not-matter", jsonpath("$.bla.bla")) // This will fail
                .end().end()

                // Now send it off to our component that does the "real sending"
                .to("direct:log")

                // Processing is done, now look at the output
                // and inform notifications
                .process(resultTransformer)
                // translate the inner stuff to json
                .marshal().json().log("Success with ${body} and ${header.Ce-Id}")
                // encode as CloudEvent
                .process(ceEncoder).to(RETURN_CHANNEL);
    }
}
