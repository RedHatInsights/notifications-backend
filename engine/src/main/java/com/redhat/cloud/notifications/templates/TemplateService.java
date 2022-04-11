package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.extensions.ActionExtension;
import com.redhat.cloud.notifications.templates.extensions.LocalDateTimeExtension;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.qute.Engine;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.ValueResolver;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.function.BiFunction;
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
                .addValueResolver(buildBiFunctionValueResolver(Context.class, "*", ActionExtension::getFromContext))
                .addValueResolver(buildBiFunctionValueResolver(Payload.class, "*", ActionExtension::getFromPayload))
                .addLocator(dbTemplateLocator)
                .build();
    }

    /*
     * When a DB template is modified (edited or deleted), its old version may still be included into another template
     * because the Qute engine has an internal cache. This scheduled method clears that cache periodically. We may want
     * to replace this with a better solution based on a Kafka topic and message broadcasting to all engine pods later.
     */
    @Scheduled(every = "${notifications.template-service.scheduled-clear.period:5m}", delayed = "${notifications.template-service.scheduled-clear.initial-delay:5m}")
    public void clearTemplates() {
        if (ConfigProvider.getConfig().getValue(USE_TEMPLATES_FROM_DB_KEY, Boolean.class)) {
            dbEngine.clearTemplates();
        }
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

    private static <T, P> ValueResolver buildBiFunctionValueResolver(Class<T> baseClass, String name, BiFunction<T, P, Object> valueTransformer) {
        return ValueResolver.builder()
                .applyToBaseClass(baseClass)
                .applyToName(name)
                .resolveSync(new Function<EvalContext, Object>() {
                    @Override
                    public Object apply(EvalContext evalContext) {
                        return valueTransformer.apply((T) evalContext.getBase(), (P) evalContext.getParams().get(0).getLiteral());
                    }
                })
                .build();
    }
}
