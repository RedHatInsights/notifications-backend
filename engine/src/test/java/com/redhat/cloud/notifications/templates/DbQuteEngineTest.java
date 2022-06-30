package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.qute.TemplateException;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.templates.TemplateService.USE_TEMPLATES_FROM_DB_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class DbQuteEngineTest {

    @Inject
    EntityManager entityManager;

    @Inject
    TemplateService templateService;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @BeforeEach
    void beforeEach() {
        System.setProperty(USE_TEMPLATES_FROM_DB_KEY, "true");
    }

    @AfterEach
    void afterEach() {
        System.clearProperty(USE_TEMPLATES_FROM_DB_KEY);
    }

    @Test
    void testIncludeExistingTemplate() {
        Template outerTemplate = createTemplate("outer-template", "Hello, {#include inner-template /}");
        Template innerTemplate = createTemplate("inner-template", "World!");
        statelessSessionFactory.withSession(statelessSession -> {
            String renderedOuterTemplate = templateService.compileTemplate(outerTemplate.getData(), outerTemplate.getName()).render();
            assertEquals("Hello, World!", renderedOuterTemplate);
        });

        /*
         * Any change to the inner template should be reflected when the outer template is rendered as long as the old
         * version of the inner template was removed from the Qute internal cache.
         */
        updateTemplateData(innerTemplate.getId(), "Red Hat!");
        statelessSessionFactory.withSession(statelessSession -> {
            String renderedOuterTemplate = templateService.compileTemplate(outerTemplate.getData(), outerTemplate.getName()).render();
            assertEquals("Hello, World!", renderedOuterTemplate);
        });
        templateService.clearTemplates();
        statelessSessionFactory.withSession(statelessSession -> {
            String renderedOuterTemplate = templateService.compileTemplate(outerTemplate.getData(), outerTemplate.getName()).render();
            assertEquals("Hello, Red Hat!", renderedOuterTemplate);
        });

        /*
         * If the inner template is deleted, the outer template rendering should fail as long as the old version of the
         * inner template was removed from the Qute internal cache.
         */
        deleteTemplate(innerTemplate.getId());
        statelessSessionFactory.withSession(statelessSession -> {
            String renderedOuterTemplate = templateService.compileTemplate(outerTemplate.getData(), outerTemplate.getName()).render();
            assertEquals("Hello, Red Hat!", renderedOuterTemplate);
        });
        templateService.clearTemplates();
        TemplateException e = assertThrows(TemplateException.class, () -> {
            statelessSessionFactory.withSession(statelessSession -> {
                templateService.compileTemplate(outerTemplate.getData(), outerTemplate.getName()).render();
            });
        });
        assertEquals("Rendering error in template [outer-template] line 1: included template [inner-template] not found", e.getMessage());
    }

    @Test
    void testIncludeUnknownTemplate() {
        Template outerTemplate = createTemplate("other-outer-template", "Hello, {#include unknown-inner-template /}");
        TemplateException e = assertThrows(TemplateException.class, () -> {
            statelessSessionFactory.withSession(statelessSession -> {
                templateService.compileTemplate(outerTemplate.getData(), outerTemplate.getName()).render();
            });
        });
        assertEquals("Rendering error in template [other-outer-template] line 1: included template [unknown-inner-template] not found", e.getMessage());
    }

    @Test
    void testToUtcFormatExtension() {
        Template template = createTemplate("to-utc-format-template", "{date.toUtcFormat()}");
        LocalDateTime date = LocalDateTime.of(2022, Month.JANUARY, 1, 0, 0);
        TemplateInstance templateInstance = templateService.compileTemplate(template.getData(), template.getName());
        assertEquals("01 Jan 2022 00:00 UTC", templateInstance.data("date", date).render());
        assertEquals("01 Jan 2022 00:00 UTC", templateInstance.data("date", date.toString()).render());
    }

    @Test
    void testToStringFormatExtension() {
        Template template = createTemplate("to-string-format-template", "{date.toStringFormat()}");
        LocalDateTime date = LocalDateTime.of(2022, Month.JANUARY, 1, 0, 0);
        TemplateInstance templateInstance = templateService.compileTemplate(template.getData(), template.getName());
        assertEquals("01 Jan 2022", templateInstance.data("date", date).render());
        assertEquals("01 Jan 2022", templateInstance.data("date", date.toString()).render());
    }

    @Test
    void testToTimeAgoExtension() {
        Template template = createTemplate("to-time-ago-template", "{date.toTimeAgo()}");
        LocalDateTime date = LocalDateTime.now().minusDays(2L);
        TemplateInstance templateInstance = templateService.compileTemplate(template.getData(), template.getName());
        assertEquals("2 days ago", templateInstance.data("date", date).render());
        assertEquals("2 days ago", templateInstance.data("date", date.toString()).render());
    }

    @Test
    void testFromIsoLocalDateTimeExtension() {
        Template template = createTemplate("from-iso-local-date-time-template", "{date.fromIsoLocalDateTime()}");
        LocalDateTime date = LocalDateTime.of(2022, Month.JANUARY, 1, 0, 0);
        TemplateInstance templateInstance = templateService.compileTemplate(template.getData(), template.getName());
        assertEquals("2022-01-01T00:00", templateInstance.data("date", date.toString()).render());
    }

    @Test
    void testActionContextExtension() {
        Template template = createTemplate("action-context-template", "{context.foo} {context.bar.baz}");
        Context context = new Context.ContextBuilder()
                .withAdditionalProperty("foo", "im foo")
                .withAdditionalProperty("bar", Map.of("baz", "im baz"))
                .build();
        TemplateInstance templateInstance = templateService.compileTemplate(template.getData(), template.getName());
        assertEquals("im foo im baz", templateInstance.data("context", context).render());
    }

    @Test
    void testActionPayloadExtension() {
        Template template = createTemplate("action-payload-template", "{payload.foo} {payload.bar.baz}");
        Payload payload = new Payload.PayloadBuilder()
                .withAdditionalProperty("foo", "im foo")
                .withAdditionalProperty("bar", Map.of("baz", "im baz"))
                .build();
        TemplateInstance templateInstance = templateService.compileTemplate(template.getData(), template.getName());
        assertEquals("im foo im baz",
                templateInstance
                .data("payload", payload)
                .render()
        );
    }

    @Transactional
    Template createTemplate(String name, String data) {
        Template template = new Template();
        template.setName(name);
        template.setDescription("The best template ever created");
        template.setData(data);
        entityManager.persist(template);
        return template;
    }

    @Transactional
    void updateTemplateData(UUID id, String data) {
        Template template = entityManager.find(Template.class, id);
        template.setData(data);
    }

    @Transactional
    void deleteTemplate(UUID id) {
        entityManager.createQuery("DELETE FROM Template WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
