package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestLifecycleManager;
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
        createTemplate("inner-template", "World!");
        String renderedOuterTemplate = templateService.compileTemplate(outerTemplate.getData(), outerTemplate.getName()).render();
        assertEquals("Hello, World!", renderedOuterTemplate);
    }

    @Test
    void testIncludeUnknownTemplate() {
        Template outerTemplate = createTemplate("other-outer-template", "Hello, {#include unknown-inner-template /}");
        TemplateException e = assertThrows(TemplateException.class, () -> {
            templateService.compileTemplate(outerTemplate.getData(), outerTemplate.getName()).render();
        });
        assertEquals("Template not found: unknown-inner-template", e.getMessage());
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

    @Transactional
    Template createTemplate(String name, String data) {
        Template template = new Template();
        template.setName(name);
        template.setData(data);
        entityManager.persist(template);
        return template;
    }
}
