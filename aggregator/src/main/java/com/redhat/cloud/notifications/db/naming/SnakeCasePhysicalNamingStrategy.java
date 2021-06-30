package com.redhat.cloud.notifications.db.naming;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import java.util.regex.Pattern;

/**
 * Inspired by Vlad Mihalcea's CamelCaseToSnakeCaseNamingStrategy.
 * https://github.com/vladmihalcea/hibernate-types/blob/master/hibernate-types-52/src/main/java/com/vladmihalcea/hibernate/type/util/CamelCaseToSnakeCaseNamingStrategy.java
 */
public class SnakeCasePhysicalNamingStrategy extends PhysicalNamingStrategyStandardImpl {

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z]+)([A-Z]+)");
    private static final String SNAKE_CASE_REGEX = "$1\\_$2";

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment context) {
        return enforceSnakeCase(super.toPhysicalColumnName(name, context));
    }

    private Identifier enforceSnakeCase(Identifier identifier) {
        if (identifier == null) {
            return null;
        } else {
            String initialName = identifier.getText();
            String modifiedName = CAMEL_CASE_PATTERN.matcher(initialName).replaceAll(SNAKE_CASE_REGEX).toLowerCase();
            if (modifiedName.equals(initialName)) {
                return identifier;
            } else {
                return Identifier.toIdentifier(modifiedName, identifier.isQuoted());
            }
        }
    }
}
