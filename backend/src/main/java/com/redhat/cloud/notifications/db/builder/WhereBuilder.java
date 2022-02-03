package com.redhat.cloud.notifications.db.builder;

public class WhereBuilder {

    private LogicOperator firstOperator;
    final Parameters parameters;
    private final StringBuilder query = new StringBuilder();

    WhereBuilder(Parameters parameters) {
        this.parameters = parameters;
    }

    public static WhereBuilder builder() {
        return new WhereBuilder(new Parameters());
    }

    public WhereBuilder ifMerge(boolean condition, WhereBuilder whereBuilder) {
        if (condition) {
            merge(whereBuilder, false);
        }

        return this;
    }

    public WhereBuilder ifElse(boolean condition, WhereBuilder ifWhereBuilder, WhereBuilder elseWhereBuilder) {
        if (condition) {
            merge(ifWhereBuilder, false);
        } else {
            merge(elseWhereBuilder, false);
        }

        return this;
    }

    public WhereBuilder ifAnd(boolean condition, String query, Object... params) {
        if (condition) {
            operation(LogicOperator.AND, query, params);
        }

        return this;
    }

    public WhereBuilder ifOr(boolean condition, String query, Object... params) {
        if (condition) {
            operation(LogicOperator.OR, query, params);
        }

        return this;
    }

    public WhereBuilder and(String query, Object... params) {
        return operation(LogicOperator.AND, query, params);
    }

    public WhereBuilder and(WhereBuilder whereBuilder) {
        return block(LogicOperator.AND, whereBuilder);
    }

    public WhereBuilder or(String query, Object... params) {
        return operation(LogicOperator.OR, query, params);
    }

    public WhereBuilder or(WhereBuilder whereBuilder) {
        return block(LogicOperator.OR, whereBuilder);
    }

    String build() {
        return query.toString();
    }

    void merge(WhereBuilder builder, boolean inBlock) {
        String subquery = builder.build();

        if (!subquery.isEmpty()) {
            addOperationIfNeeded(builder.firstOperator);
            if (inBlock) {
                query.append("(");
            }

            query.append(subquery);

            if (inBlock) {
                query.append(")");
            }

            parameters.merge(builder.parameters);
        }
    }

    private void addOperationIfNeeded(LogicOperator operator) {
        if (this.query.length() > 0) {
            this.query
                    .append(" ")
                    .append(operator.name())
                    .append(" ");
        } else {
            firstOperator = operator;
        }
    }

    private WhereBuilder block(LogicOperator operator, WhereBuilder whereBuilder) {
        whereBuilder.firstOperator = operator;
        this.merge(whereBuilder, true);

        return this;
    }

    private WhereBuilder operation(LogicOperator operator, String query, Object... params) {
        addOperationIfNeeded(operator);
        this.query.append(query);
        this.parameters.addParams(params);

        return this;
    }
}
