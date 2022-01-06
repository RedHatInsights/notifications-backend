package com.redhat.cloud.notifications.db.builder;

import com.redhat.cloud.notifications.db.Query;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Map;
import java.util.function.BiFunction;

public class QueryBuilder<T> {

    private final String rawSelect;

    private String rawSort = "";
    private final WhereBuilder whereBuilder;
    private final JoinBuilder joinBuilder;
    private final Parameters parameters;
    private final Class<T> resultType;

    private String alias;
    private Integer maxResult;
    private Integer firstResult;

    private QueryBuilder(Class<T> resultType, String baseQuery) {
        this.rawSelect = baseQuery;
        parameters = new Parameters();
        whereBuilder = new WhereBuilder(parameters);
        joinBuilder = new JoinBuilder();
        this.resultType = resultType;
    }

    public static <T> QueryBuilder<T> builder(Class<T> resultType, String query) {
        return new QueryBuilder<T>(resultType, query);
    }

    public static <T> QueryBuilder<T> builder(Class<T> resultType) {
        return new QueryBuilder<T>(resultType, null);
    }

    public QueryBuilder<T> where(WhereBuilder whereBuilder) {
        this.whereBuilder.merge(whereBuilder, false);
        return this;
    }

    public QueryBuilder<T> join(JoinBuilder joinBuilder) {
        this.joinBuilder.merge(joinBuilder);
        return this;
    }

    public QueryBuilder<T> alias(String alias) {
        this.alias = alias;

        return this;
    }

    public QueryBuilder<T> limit(Query limiter) {
        if (limiter != null) {
            rawSort = " " + limiter.getSortQuery();
            if (limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
                maxResult = limiter.getLimit().getLimit();
                firstResult = limiter.getLimit().getOffset();
            }
        }

        return this;
    }

    public Mutiny.Query<T> build(BiFunction<String, Class<T>, Mutiny.Query<T>> queryCreator) {
        Mutiny.Query<T> query = queryCreator.apply(buildRawQuery(), resultType);
        parameters.forEach(query::setParameter);

        if (maxResult != null) {
            query.setMaxResults(maxResult);
        }

        if (firstResult != null) {
            query.setFirstResult(firstResult);
        }

        return query;
    }

    public Mutiny.Query<Long> buildCount(BiFunction<String, Class<Long>, Mutiny.Query<Long>> queryCreator) {
        Mutiny.Query<Long> query = queryCreator.apply(buildRawCountQuery(), Long.class);
        parameters.forEach(query::setParameter);

        return query;
    }

    String buildRawQuery() {
        StringBuilder rawQuery = new StringBuilder();
        if (rawSelect == null) {
            if (alias != null) {
                rawQuery.append("SELECT ").append(alias).append(" ");
            }

            rawQuery.append("FROM ").append(resultType.getSimpleName());
            if (alias != null) {
                rawQuery.append(" ").append(alias);
            }
        } else {
            rawQuery.append(rawSelect);
        }

        appendRemaining(rawQuery);
        return rawQuery.toString();
    }

    String buildRawCountQuery() {
        StringBuilder rawQuery = new StringBuilder();
        rawQuery.append("SELECT COUNT(*) FROM ").append(resultType.getSimpleName());
        if (alias != null) {
            rawQuery.append(" ").append(alias);
        }

        appendRemaining(rawQuery);
        return rawQuery.toString();
    }

    Map<String, Object> getParameters() {
        return parameters.mapCopy();
    }

    private void appendRemaining(StringBuilder rawQuery) {
        rawQuery.append(joinBuilder.build());

        String rawWhere = whereBuilder.build();
        if (!rawWhere.isEmpty()) {
            rawQuery.append(" WHERE ").append(rawWhere);
        }

        rawQuery.append(rawSort);
    }

}
