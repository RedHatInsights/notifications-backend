package com.redhat.cloud.notifications.db.builder;

import com.redhat.cloud.notifications.db.Query;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class QueryBuilder {

    private String rawQuery;
    private final Map<String, Object> parameters = new HashMap<>();
    private Integer maxResult;
    private Integer firstResult;

    private QueryBuilder(String query, Object... params) {
        this.rawQuery = query;
        addParams(params);
    }

    public static QueryBuilder builder(String query, Object ... params) {
        return new QueryBuilder(query, params);
    }

    public QueryBuilder addIfTrue(boolean condition, String queryExtension, Object ... params) {
        if (condition) {
            rawQuery += queryExtension;
            addParams(params);
        }

        return this;
    }

    public QueryBuilder addIfTrue(boolean condition, Supplier<String> queryExtension, Object ... params) {
        if (condition) {
            addIfTrue(true, queryExtension.get(), params);
        }

        return this;
    }

    public QueryBuilder addIfNotNull(Object ifNotNull, String queryExtension, Object ... params) {
        return this.addIfTrue(ifNotNull != null, queryExtension, params);
    }

    public QueryBuilder addIfNotNull(Object ifNotNull, Supplier<String> queryExtension, Object ... params) {
        return this.addIfTrue(ifNotNull != null, queryExtension, params);
    }

    public QueryBuilder addLimiter(Query limiter) {
        if (limiter != null) {
            this.addIfTrue(true, limiter.getSortQuery());
            if (limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
                maxResult = limiter.getLimit().getLimit();
                firstResult = limiter.getLimit().getOffset();
            }
        }

        return this;
    }

    public <T> Mutiny.Query<T> build(BiFunction<String, Class<T>, Mutiny.Query<T>> queryCreator, Class<T> klass) {
        Mutiny.Query<T> query = queryCreator.apply(this.rawQuery, klass);
        parameters.forEach(query::setParameter);

        if (maxResult != null) {
            query.setMaxResults(maxResult);
        }

        if (firstResult != null) {
            query.setFirstResult(firstResult);
        }

        return query;
    }

    public void addParams(Object ... params) {
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("Odd number of params. It needs to be a pair number of params. The key followed by the value");
        }

        for (int i = 0; i < params.length; i += 2) {
            String key = params[i].toString();
            if (parameters.containsKey(key)) {
                throw new IllegalArgumentException(String.format("Parameter [%s] already exists", key));
            }
            parameters.put(params[i].toString(), params[i + 1]);
        }
    }
}
