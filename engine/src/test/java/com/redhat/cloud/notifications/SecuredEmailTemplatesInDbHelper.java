package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.templates.EmailTemplateMigrationService;
import com.redhat.cloud.notifications.templates.TemplateService;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.util.Map;

public abstract class SecuredEmailTemplatesInDbHelper {

    protected static final String BUNDLE_RHEL = "rhel";

    protected static final String COMMON_SECURED_LABEL_CHECK = "common template for secured env";

    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = false;

    @Inject
    Environment environment;

    @Inject
    protected ResourceHelpers resourceHelpers;

    @Inject
    protected StatelessSessionFactory statelessSessionFactory;

    @InjectSpy
    protected TemplateRepository templateRepository;

    @Inject
    protected TemplateService templateService;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EmailTemplateMigrationService emailTemplateMigrationService;

    @BeforeEach
    void initData() {
        Bundle bundle = null;
        try {
            bundle = resourceHelpers.findBundle(getBundle());
        } catch (NoResultException nre) {
            bundle = resourceHelpers.createBundle(getBundle());
        }

        try {
            resourceHelpers.findApp(getBundle(), getApp());
        } catch (NoResultException nre) {
            resourceHelpers.createApp(bundle.getId(), getApp());
        }
        featureFlipper.setUseSecuredEmailTemplates(true);
        emailTemplateMigrationService.migrate();
        featureFlipper.setUseTemplatesFromDb(true);
    }

    @AfterEach
    void restoreEnv() {
        featureFlipper.setUseSecuredEmailTemplates(false);
        featureFlipper.setUseTemplatesFromDb(false);
    }

    protected String generateEmail(TemplateInstance template, Action action) {
        return template
            .data("action", action)
            .data("environment", environment)
            .render();
    }

    protected String generateEmail(TemplateInstance templateInstance, Map<String, Object> context) {
        return templateInstance
            .data("action", Map.of("context", context, "bundle", getBundle()))
            .data("environment", environment)
            .render();
    }

    protected String getBundle() {
        return BUNDLE_RHEL;
    }

    protected String getApp() {
        return null;
    }

    protected void writeEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
            TestHelpers.writeEmailTemplate(result, fileName);
        }
    }
}
