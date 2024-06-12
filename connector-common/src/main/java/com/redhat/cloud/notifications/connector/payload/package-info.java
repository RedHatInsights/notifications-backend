/**
 * The package defines the classes required to be able to properly request and
 * process the event's payload from the engine. The goal is to make it
 * transparent for the rest of the processors, by simply appending the incoming
 * payload to the original Cloud Event that comes from Kafka.
 */
package com.redhat.cloud.notifications.connector.payload;
