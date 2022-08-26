package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class IntegrationTemplateRepositoryTest extends DbIsolatedTest {

    public static final String SPECIFIC_SLACK = "specific-slack";
    public static final String SLACK = "slack";
    public static final String GENERIC_SLACK = "generic-slack";

    @Inject
    EntityManager entityManager;

    @Inject
    TemplateRepository templateRepository;

    @Test
    void testMostSpecificOneIsUsed() {

        Template specificSlack = createTemplate(SPECIFIC_SLACK, "Just a test", "Li la lu");
        createIntegrationTemplate(specificSlack, IntegrationTemplate.TemplateKind.APPLICATION, SLACK);
        Template genericSlack = createTemplate(GENERIC_SLACK, "The default", "The default");
        createIntegrationTemplate(genericSlack, IntegrationTemplate.TemplateKind.DEFAULT, SLACK);

        Optional<IntegrationTemplate> ito = templateRepository.findIntegrationTemplate(null, null, IntegrationTemplate.TemplateKind.APPLICATION, SLACK);
        assertTrue(ito.isPresent());
        IntegrationTemplate it = ito.get();
        assertEquals(it.getTemplateKind(), IntegrationTemplate.TemplateKind.APPLICATION);
        assertEquals(it.getTheTemplate().getName(), SPECIFIC_SLACK);

        // This should be the default
        ito = templateRepository.findIntegrationTemplate(null, null, IntegrationTemplate.TemplateKind.DEFAULT, SLACK);
        assertTrue(ito.isPresent());
        it = ito.get();
        assertEquals(it.getTemplateKind(), IntegrationTemplate.TemplateKind.DEFAULT);
        assertEquals(it.getTheTemplate().getName(), GENERIC_SLACK);

    }

    @Test
    void testUserFallback() {

        Template specificSlack = createTemplate(SPECIFIC_SLACK, "Just a test", "Li la lu");
        createIntegrationTemplate(specificSlack, IntegrationTemplate.TemplateKind.ORG, SLACK, "user-123", "org-id-123");
        Template genericSlack = createTemplate(GENERIC_SLACK, "The default", "The default");
        createIntegrationTemplate(genericSlack, IntegrationTemplate.TemplateKind.DEFAULT, SLACK);

        Optional<IntegrationTemplate> ito = templateRepository.findIntegrationTemplate(null,
                "org-id-123", IntegrationTemplate.TemplateKind.ORG, SLACK);
        assertTrue(ito.isPresent());
        IntegrationTemplate it = ito.get();
        assertEquals(IntegrationTemplate.TemplateKind.ORG, it.getTemplateKind());
        assertEquals(SPECIFIC_SLACK, it.getTheTemplate().getName());

        // This should be the default
        ito = templateRepository.findIntegrationTemplate(null, null, IntegrationTemplate.TemplateKind.DEFAULT, SLACK);
        assertTrue(ito.isPresent());
        it = ito.get();
        assertEquals(IntegrationTemplate.TemplateKind.DEFAULT, it.getTemplateKind());
        assertEquals(GENERIC_SLACK, it.getTheTemplate().getName());

        // Now see if we fall back to default if the user has no template
        ito = templateRepository.findIntegrationTemplate(null,
                "unknown-org-id", IntegrationTemplate.TemplateKind.ORG, SLACK);
        assertTrue(ito.isPresent());
        it = ito.get();
        assertEquals(IntegrationTemplate.TemplateKind.DEFAULT, it.getTemplateKind());
        assertEquals(GENERIC_SLACK, it.getTheTemplate().getName());

    }

    @Transactional
    Template createTemplate(String name, String description, String data) {
        Template template = new Template();
        template.setName(name);
        template.setDescription(description);
        template.setData(data);
        entityManager.persist(template);
        return template;
    }

    @Transactional
    IntegrationTemplate createIntegrationTemplate(Template template, IntegrationTemplate.TemplateKind kind, String iType, String account, String orgId) {
        IntegrationTemplate gt = new IntegrationTemplate();
        gt.setTemplateKind(kind);
        gt.setIntegrationType(iType);
        gt.setTheTemplate(template);
        if (account != null) {
            gt.setAccountId(account);
        }
        if (orgId != null) {
            gt.setOrgId(orgId);
        }
        entityManager.persist(gt);
        return gt;
    }

    IntegrationTemplate createIntegrationTemplate(Template template, IntegrationTemplate.TemplateKind kind, String iType) {
        return createIntegrationTemplate(template, kind, iType, null, null);
    }

}
