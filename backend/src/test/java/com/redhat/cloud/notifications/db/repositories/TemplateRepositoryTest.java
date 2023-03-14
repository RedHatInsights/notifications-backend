package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.models.TemplateVersion;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TemplateRepositoryTest {

    public static final String TEST_TEMPLATE_NAME = "test-template-name";

    @Inject
    EntityManager entityManager;

    @Inject
    TemplateRepository templateRepository;

    @Test
    void testCreateUpdateTemplate() {
        final String templateData = "Li la lu";
        final String templateDataUpdated = "Li la lu la";

        Template template = new Template();
        template.setName(TEST_TEMPLATE_NAME);
        template.setDescription("Just a test");
        template.setData(templateData);

        Template createdTemplate = templateRepository.createTemplate(template);
        TemplateVersion templateVersion = template.getTemplateCurrentVersion();
        assertNotNull(templateVersion);
        assertEquals(templateData, template.getData());
        assertEquals(templateData, templateVersion.getData());
        assertEquals(0, templateVersion.getVersion());

        Template updateTemplate = new Template();
        updateTemplate.setName(TEST_TEMPLATE_NAME);
        updateTemplate.setDescription("Just another test");
        updateTemplate.setData(templateDataUpdated + "first");
        templateRepository.updateTemplate(createdTemplate.getId(), updateTemplate);
        updateTemplate.setData(templateDataUpdated);
        templateRepository.updateTemplate(createdTemplate.getId(), updateTemplate);

        assertEquals(3, getTemplateVersionsFromDb(createdTemplate.getId()).size());

        Template updateTemplateFromDB = templateRepository.findTemplateById(createdTemplate.getId());
        assertEquals(templateDataUpdated, updateTemplateFromDB.getData());
        assertEquals(2, updateTemplateFromDB.getTemplateCurrentVersion().getVersion());

        deleteTemplateVersion(getTemplateVersionsFromDb(updateTemplateFromDB.getId()).get(0));
        assertEquals(2, getTemplateVersionsFromDb(createdTemplate.getId()).size());

        templateRepository.deleteTemplate(createdTemplate.getId());
        assertEquals(0, getTemplateVersionsFromDb(createdTemplate.getId()).size());
    }

    @Transactional
    void deleteTemplateVersion(TemplateVersion templateVersion) {
        entityManager.createQuery("DELETE FROM TemplateVersion WHERE id = :id").setParameter("id", templateVersion.getId()).executeUpdate();
    }

    List<TemplateVersion> getTemplateVersionsFromDb(UUID parentTemplateId) {
        return entityManager.createQuery("SELECT t from TemplateVersion t WHERE parentTemplate.id = : id order by version", TemplateVersion.class).setParameter("id", parentTemplateId).getResultList();
    }
}
