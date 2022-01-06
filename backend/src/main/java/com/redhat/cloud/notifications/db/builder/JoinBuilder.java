package com.redhat.cloud.notifications.db.builder;

public class JoinBuilder {

    private final StringBuilder query = new StringBuilder();

    JoinBuilder() {

    }

    public static JoinBuilder builder() {
        return new JoinBuilder();
    }

    public JoinBuilder leftJoin(String what) {
        addSpaceIfNeeded();
        query.append("LEFT JOIN ").append(what);

        return this;
    }

    public JoinBuilder join(String what) {
        addSpaceIfNeeded();
        query.append("JOIN ").append(what);

        return this;
    }

    public JoinBuilder ifLeftJoin(boolean condition, String what) {
        if (condition) {
            leftJoin(what);
        }

        return this;
    }

    public JoinBuilder ifJoin(boolean condition, String what) {
        if (condition) {
            join(what);
        }

        return this;
    }

    public JoinBuilder if_(boolean condition, JoinBuilder joinBuilder) {
        if (condition) {
            merge(joinBuilder);
        }

        return this;
    }

    void merge(JoinBuilder joinBuilder) {
        String subquery = joinBuilder.build();
        if (!subquery.isEmpty()) {
            query.append(" ");
            query.append(subquery);
        }
    }

    String build() {
        return query.toString();
    }

    private void addSpaceIfNeeded() {
        if (query.length() > 0) {
            query.append(" ");
        }
    }
}
