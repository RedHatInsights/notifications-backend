package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.extensions.LocalDateTimeExtension;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.qute.Engine;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.ValueResolver;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.function.Function;

@ApplicationScoped
public class TemplateService {

    public static final String USE_TEMPLATES_FROM_DB_KEY = "notifications.use-templates-from-db";

    private static final Logger LOGGER = Logger.getLogger(TemplateService.class);

    @Inject
    Engine engine;

    @Inject
    Environment environment;

    @Inject
    DbTemplateLocator dbTemplateLocator;

    Engine dbEngine;

    @PostConstruct
    void postConstruct() {
        if (ConfigProvider.getConfig().getValue(USE_TEMPLATES_FROM_DB_KEY, Boolean.class)) {
            LOGGER.info("Using templates from the database");
        }
        dbEngine = Engine.builder()
                .addDefaults()
                .addValueResolver(new ReflectionValueResolver()) // Recommended by the Qute doc.
                .addValueResolver(buildValueResolver(LocalDateTime.class, "toUtcFormat", LocalDateTimeExtension::toUtcFormat))
                .addValueResolver(buildValueResolver(String.class, "toUtcFormat", LocalDateTimeExtension::toUtcFormat))
                .addValueResolver(buildValueResolver(LocalDateTime.class, "toStringFormat", LocalDateTimeExtension::toStringFormat))
                .addValueResolver(buildValueResolver(String.class, "toStringFormat", LocalDateTimeExtension::toStringFormat))
                .addValueResolver(buildValueResolver(LocalDateTime.class, "toTimeAgo", LocalDateTimeExtension::toTimeAgo))
                .addValueResolver(buildValueResolver(String.class, "toTimeAgo", LocalDateTimeExtension::toTimeAgo))
                .addValueResolver(buildValueResolver(String.class, "fromIsoLocalDateTime", LocalDateTimeExtension::fromIsoLocalDateTime))
                .addLocator(dbTemplateLocator)
                .build();
    }

    public TemplateInstance compileTemplate(String template, String name) {
        return getEngine().parse(template, null, name).instance();
    }

    private Engine getEngine() {
        if (ConfigProvider.getConfig().getValue(USE_TEMPLATES_FROM_DB_KEY, Boolean.class)) {
            return dbEngine;
        } else {
            return engine;
        }
    }

    public String renderTemplate(User user, Action action, TemplateInstance templateInstance) {
        return templateInstance
                .data("action", action)
                .data("user", user)
                .data("environment", environment)
                .render();
    }

    private static <T> ValueResolver buildValueResolver(Class<T> baseClass, String extensionName, Function<T, Object> valueTransformer) {
        return ValueResolver.builder()
                .applyToBaseClass(baseClass)
                .applyToName(extensionName)
                .resolveSync(new Function<EvalContext, Object>() {
                    @Override
                    public Object apply(EvalContext evalContext) {
                        return valueTransformer.apply((T) evalContext.getBase());
                    }
                })
                .build();
    }
}
