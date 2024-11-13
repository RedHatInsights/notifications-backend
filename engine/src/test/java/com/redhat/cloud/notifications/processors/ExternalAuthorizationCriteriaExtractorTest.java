package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class ExternalAuthorizationCriteriaExtractorTest {

    @Inject
    ExternalAuthorizationCriterionExtractor externalAuthorizationCriteriaExtractor;

    @Inject
    BaseTransformer baseTransformer;

    @Test
    void testExtractionFromEvent() {
        Context context = null;
        assertNull(externalAuthorizationCriteriaExtractor.extract(createEvent(context)));

        context = new Context.ContextBuilder()
            .withAdditionalProperty("not_null_context", "123456")
            .build();
        assertNull(externalAuthorizationCriteriaExtractor.extract(createEvent(context)));

        context = new Context.ContextBuilder()
            .withAdditionalProperty(ExternalAuthorizationCriterionExtractor.EXTERNAL_AUTHORIZATION_CRITERIA, null)
            .build();
        assertNull(externalAuthorizationCriteriaExtractor.extract(createEvent(context)));

        ExternalAuthorizationCriterion.Type kesselAssetType = new ExternalAuthorizationCriterion.Type(RandomStringUtils.randomAlphanumeric(10), RandomStringUtils.randomAlphanumeric(10));
        ExternalAuthorizationCriterion externalAuthorizationCriterion = new ExternalAuthorizationCriterion(kesselAssetType, RandomStringUtils.randomAlphanumeric(20), RandomStringUtils.randomAlphanumeric(10));

        context = new Context.ContextBuilder()
            .withAdditionalProperty(ExternalAuthorizationCriterionExtractor.EXTERNAL_AUTHORIZATION_CRITERIA, Map.of("type", externalAuthorizationCriterion.getType(), "id", externalAuthorizationCriterion.getId(), "relation", externalAuthorizationCriterion.getRelation()))
            .build();
        assertEquals(externalAuthorizationCriterion, externalAuthorizationCriteriaExtractor.extract(createEvent(context)));
    }

    @Test
    void testExtractionFromEmailAggregation() {
        Context context = null;
        assertNull(externalAuthorizationCriteriaExtractor.extract(createEmailAggregation(context)));

        context = new Context.ContextBuilder()
            .withAdditionalProperty("not_null_context", "123456")
            .build();
        assertNull(externalAuthorizationCriteriaExtractor.extract(createEmailAggregation(context)));

        context = new Context.ContextBuilder()
            .withAdditionalProperty(ExternalAuthorizationCriterionExtractor.EXTERNAL_AUTHORIZATION_CRITERIA, null)
            .build();
        assertNull(externalAuthorizationCriteriaExtractor.extract(createEmailAggregation(context)));

        ExternalAuthorizationCriterion.Type kesselAssetType = new ExternalAuthorizationCriterion.Type(RandomStringUtils.randomAlphanumeric(10), RandomStringUtils.randomAlphanumeric(10));
        ExternalAuthorizationCriterion externalAuthorizationCriterion = new ExternalAuthorizationCriterion(kesselAssetType, RandomStringUtils.randomAlphanumeric(20), RandomStringUtils.randomAlphanumeric(10));

        context = new Context.ContextBuilder()
            .withAdditionalProperty(ExternalAuthorizationCriterionExtractor.EXTERNAL_AUTHORIZATION_CRITERIA, Map.of("type", externalAuthorizationCriterion.getType(), "id", externalAuthorizationCriterion.getId(), "relation", externalAuthorizationCriterion.getRelation()))
            .build();
        assertEquals(externalAuthorizationCriterion, externalAuthorizationCriteriaExtractor.extract(createEmailAggregation(context)));
    }

    private EmailAggregation createEmailAggregation(Context context) {
        EmailAggregation emailAggregation = new EmailAggregation();
        emailAggregation.setPayload(baseTransformer.toJsonObject(createEvent(context)));
        return emailAggregation;
    }

    private Event createEvent(Context context) {
        String orgId = RandomStringUtils.randomNumeric(6);
        Action action = new Action.ActionBuilder()
            .withBundle("rhel")
            .withApplication("policies")
            .withEventType("policy-triggered")
            .withOrgId(orgId)
            .withTimestamp(LocalDateTime.now(UTC))
            .withContext(context)
            .withEvents(List.of(
                new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(new Payload.PayloadBuilder()
                        .withAdditionalProperty("foo", "bar")
                        .build()
                    ).build()
            )).build();

        final EventType eventType = new EventType();
        eventType.setId(UUID.randomUUID());

        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setEventWrapper(new EventWrapperAction(action));
        event.setEventType(eventType);
        event.setOrgId(orgId);
        return event;
    }
}
