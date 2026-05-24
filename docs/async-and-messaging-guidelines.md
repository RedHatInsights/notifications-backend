# Async and Messaging Guidelines

## Architecture Overview

This repo has two connector frameworks. New connectors use v2 (`connector-common-v2`, SmallRye Reactive Messaging). The email connector still uses v1 (`connector-common`, Apache Camel routes). Do not mix frameworks within a single connector module.

## Kafka Topics and Channel Naming

### Engine channels (defined in `engine/src/main/resources/application.properties`)

- `ingress` -- incoming events from `platform.notifications.ingress` (consumer group `integrations`)
- `egress` -- outgoing to `platform.notifications.ingress` (self-referencing for internal events)
- `tocamel` -- outgoing to `platform.notifications.tocamel` (engine-to-connectors, snappy compression)
- `highvolume` -- outgoing to `platform.notifications.connector.email.high.volume` (high-traffic email path)
- `fromcamel` -- incoming from `platform.notifications.fromcamel` (connector results back to engine)
- `exportrequests` -- incoming from `platform.export.requests`

### V2 connector channels (defined in `connector-common-v2/src/main/resources/application.properties`)

- `incomingmessages` -- reads from `platform.notifications.tocamel`
- `outgoingmessages` -- writes to `platform.notifications.fromcamel` (structured CloudEvents mode)

Connector-specific properties (group ID, port, concurrency) go in each connector's own `application.properties`. Prefer referencing the connector name via `${notifications.connector.name}`.

## V2 Connector Pattern (Preferred for New Connectors)

Extend these three classes from `connector-common-v2`:

1. **`MessageHandler`** -- Override `handle(IncomingCloudEventMetadata<JsonObject>)` to perform the external call. Return `HandledMessageDetails` (or `HandledHttpMessageDetails` for HTTP connectors). Mark as `@ApplicationScoped`.
2. **`ExceptionHandler`** -- Override `process(Throwable, IncomingCloudEventMetadata<JsonObject>)` to customize error logging/details. Mark as `@ApplicationScoped @Alternative @Priority(0)`.
3. **`ConnectorConfig`** -- Override only if the connector needs extra config properties. Use `@Priority` higher than `BASE_CONFIG_PRIORITY`.

Do not touch `MessageConsumer` or `OutgoingMessageSender` -- those are framework classes in `connector-common-v2`.

### V2 concurrency

Set `smallrye.messaging.worker.connector-thread-pool.max-concurrency` in the connector's `application.properties`. The `MessageConsumer.processMessage` method uses `@Blocking("connector-thread-pool")` and `@RunOnVirtualThread`.

## V1 Connector Pattern (Camel, Email Only)

Extend `EngineToConnectorRouteBuilder` and implement `configureRoutes()`. The base class provides:
- Kafka consumer -> `IncomingCloudEventFilter` -> `IncomingCloudEventProcessor` -> SEDA queue
- Exception handling with configurable redelivery and Kafka reinjection

Route processing happens on SEDA threads. Configure parallelism via `notifications.connector.seda.concurrent-consumers` and `notifications.connector.seda.queue-size`.

After processing, route to `direct(SUCCESS)` for the success path. The `ConnectorToEngineRouteBuilder` builds the outgoing CloudEvent and sends it to the outgoing Kafka topic.

## CloudEvents Envelope

All engine-to-connector messages use structured CloudEvents mode with type prefix `com.redhat.console.notification.toCamel.`. The connector name is appended (e.g., `...toCamel.slack`).

All connector-to-engine responses use type `com.redhat.console.notifications.history` with spec version `1.0`.

Incoming channels on the engine disable CloudEvents parsing (`cloud-events=false`) -- the engine parses CloudEvents manually.

## Connector Header Filtering

Every Kafka message from the engine carries an `x-rh-notifications-connector` header identifying the target connector. Both v1 (`IncomingCloudEventFilter`) and v2 (`MessageConsumer.extractConnectorHeader`) check this header against `notifications.connector.supported-connector-headers`. Messages for other connectors are silently dropped.

## Engine Event Consumption

`EventConsumer` reads from the `ingress` channel with `@Incoming` and `@Blocking`. It supports an async processing mode (toggled via Unleash `async-event-processing`) that offloads work to a `ThreadPoolExecutor` with a blocking `LinkedBlockingQueue` to apply backpressure.

When adding new processing steps, add them in the `process(Message<String>)` method. Always annotate with `@ActivateRequestContext` when the method needs a CDI request context.

## Emitting Messages to Kafka

Use `@Channel` + `Emitter<T>` for programmatic message sending. Attach metadata using the builder pattern:
```java
Message.of(payload)
    .addMetadata(OutgoingKafkaRecordMetadata.<String>builder().withHeaders(headers).build())
    .addMetadata(OutgoingCloudEventMetadata.<String>builder().withId(id).withType(type).build())
    .addMetadata(TracingMetadata.withPrevious(Context.current()));
```

Use `KafkaMessageWithIdBuilder.build(payload)` when emitting internal events to `egress` -- it auto-generates a `rh-message-id` header.

## High-Volume Traffic Routing

Events from `errata-notifications` (the `HIGH_VOLUME_APPLICATION`) are routed to the `highvolume` channel when the feature toggle is enabled. Only the email connector supports this path. Check `ConnectorSender.isEventFromHighVolumeApplication` and `isConnectorCompatibleWithHighVolumeTopic` before adding new high-volume routes.

## Kafka Channel Pause/Resume

`KafkaChannelManager` dynamically pauses/resumes Kafka channels using the Unleash feature toggle `notifications.kafka-channels`. Channels must be marked `pausable=true` in `application.properties` to participate. This is used for operational control, not application logic.

## Aggregator (Cron Job)

`DailyEmailAggregationJob` is a scheduled batch job (not a Kafka consumer). It queries the DB for pending aggregations, builds `Action` messages, and emits them to the `egress` channel using `@Channel(EGRESS_CHANNEL) Emitter<String>`. It pushes metrics to Prometheus Pushgateway after each run.

## Kafka Reinjection (V1 Only)

When a v1 connector fails to deliver, `ExceptionProcessor` reinjects the original CloudEvent back to the incoming Kafka topic with an exponential delay (10s, 30s, 1min). The reinjection count is tracked via the `x-rh-notifications-connector-reinjections-count` Kafka header. Maximum reinjections default to 3 (configurable via `notifications.connector.kafka.maximum-reinjections`). Set to `0` for connectors that should not retry (e.g., email).

## Testing

### V1 connectors
Extend `ConnectorRoutesTest` (from `connector-common/src/test`). It provides Camel `AdviceWith` helpers to mock Kafka source/sink and remote server endpoints. Tests use `CamelQuarkusTestSupport` and send messages via `template.sendBodyAndHeaders(KAFKA_SOURCE_MOCK, ...)`.

### V2 connectors
Extend `BaseConnectorIntegrationTest` (from `connector-common-v2/src/test`). It uses `InMemoryConnector` to replace Kafka channels. Send messages via `incomingMessageSource.send(Message.of(...))` with `IncomingCloudEventMetadata` and `OutgoingKafkaRecordMetadata` attached. Assert responses via `outgoingMessageSink`.

Both use `MockServerLifecycleManager` (WireMock) for HTTP target simulation and `MicrometerAssertionHelper` for metrics verification.

## Verification

```bash
# Compile all connector modules
./mvnw -pl connector-common,connector-common-v2 compile

# Run v1 connector tests (e.g., email)
./mvnw -pl connector-email test

# Run v2 connector tests (e.g., slack, webhook)
./mvnw -pl connector-slack test
./mvnw -pl connector-webhook test

# Run engine messaging tests
./mvnw -pl engine test -Dtest=EventConsumerTest,ConnectorSenderTest

# Verify Kafka channel config consistency
grep -r "mp.messaging" engine/src/main/resources/application.properties
```
