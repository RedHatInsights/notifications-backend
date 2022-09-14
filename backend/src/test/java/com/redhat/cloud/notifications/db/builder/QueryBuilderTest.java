package com.redhat.cloud.notifications.db.builder;

import com.redhat.cloud.notifications.db.Query;
import org.junit.jupiter.api.Test;

import javax.persistence.TypedQuery;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;


public class QueryBuilderTest {

    @Test
    public void testSimpleQuery() {
        assertEquals(
                "SELECT f FROM foo f",
                QueryBuilder.builder(Object.class, "SELECT f FROM foo f").buildRawQuery()
        );
    }

    @Test
    public void testQueryWithAnds() {
        QueryBuilder<Object> builder = QueryBuilder.builder(Object.class, "SELECT f FROM foo f")
            .where(WhereBuilder.builder()
                    .and("abc = :abc", "abc", "my-abc")
                    .and("xyz = :etc", "etc", "my-etc")
        );

        assertEquals(
                "SELECT f FROM foo f WHERE abc = :abc AND xyz = :etc",
                builder.buildRawQuery()
        );

        assertEquals(
                Map.of(
                        "abc", "my-abc",
                        "etc", "my-etc"
                ),
                builder.getParameters()
        );
    }

    @Test
    public void testQueryWithConditionals() {
        QueryBuilder<Object> builder = QueryBuilder.builder(Object.class, "SELECT f FROM foo f")
                .where(WhereBuilder.builder()
                        .and("abc = :abc", "abc", "my-abc")
                        .ifMerge(
                                false,
                                WhereBuilder.builder()
                                        .and("xyz = :etc", "etc", "ignored")
                                        .and("ignored")
                                        .or("ignored")
                        )
                );

        assertEquals(
                "SELECT f FROM foo f WHERE abc = :abc",
                builder.buildRawQuery()
        );

        assertEquals(
                Map.of(
                        "abc", "my-abc"
                ),
                builder.getParameters()
        );
    }

    @Test
    public void testQueryWithShortConditionals() {
        QueryBuilder<Object> builder = QueryBuilder.builder(Object.class, "SELECT f FROM foo f")
                .where(WhereBuilder.builder()
                        .and("abc = :abc", "abc", "my-abc")
                        .ifAnd(true, "xyz = :etc", "etc", "my-etc")
                        .ifAnd(false, "ignored", "ignored", "ignored")
                );

        assertEquals(
                "SELECT f FROM foo f WHERE abc = :abc AND xyz = :etc",
                builder.buildRawQuery()
        );

        assertEquals(
                Map.of(
                        "abc", "my-abc",
                        "etc", "my-etc"
                ),
                builder.getParameters()
        );
    }

    @Test
    public void testComplexQuery() {
        QueryBuilder<Object> builder = QueryBuilder.builder(Object.class, "SELECT f FROM foo f")
                .where(
                        WhereBuilder.builder()
                                .and("abc = :abc", "abc", "my-abc")
                                .and(
                                        WhereBuilder.builder()
                                                .and("bar = :bar", "bar", "is-bar")
                                                .or("baz = :baz", "baz", "is-baz")
                                                .and("(account_id IS NULL OR account_id = :account_id)", "account_id", "007")
                                                .or(
                                                        WhereBuilder.builder().or(WhereBuilder.builder().or("this = :that", "that", "yes-that"))
                                                )
                                )
                );

        assertEquals(
                "SELECT f FROM foo f WHERE abc = :abc AND (bar = :bar OR baz = :baz AND (account_id IS NULL OR account_id = :account_id) OR ((this = :that)))",
                builder.buildRawQuery()
        );

        assertEquals(
                Map.of(
                        "abc", "my-abc",
                        "bar", "is-bar",
                        "baz", "is-baz",
                        "account_id", "007",
                        "that", "yes-that"
                ),
                builder.getParameters()
        );
    }

    @Test
    public void testUsingObject() {
        assertEquals(
                "FROM Object",
                QueryBuilder.builder(Object.class).buildRawQuery()
        );
    }

    @Test
    public void testUsingObjectWithAlias() {
        assertEquals(
                "SELECT myalias FROM Object myalias",
                QueryBuilder.builder(Object.class).alias("myalias").buildRawQuery()
        );
    }

    @Test
    public void testUsingCount() {
        assertEquals(
                "SELECT COUNT(*) FROM Object",
                QueryBuilder.builder(Object.class).buildRawCountQuery()
        );
    }

    @Test
    public void testUsingCountAndAlias() {
        assertEquals(
                "SELECT COUNT(*) FROM Object myalias",
                QueryBuilder.builder(Object.class).alias("myalias").buildRawCountQuery()
        );
    }

    @Test
    public void usingJoin() {
        assertEquals(
                "SELECT o FROM Object o LEFT JOIN FETCH o.foo JOIN o.bar",
                QueryBuilder.builder(Object.class).alias("o")
                        .join(
                                JoinBuilder.builder()
                                        .leftJoinFetch("o.foo")
                                        .ifMerge(true, JoinBuilder.builder().join("o.bar"))
                                        .ifMerge(false, JoinBuilder.builder().join("Ignoring this").leftJoin("and this"))
                        )
                        .buildRawQuery()
        );
    }

    @Test
    public void usingQueryLimit() {
        TypedQuery<Object> query = mock(TypedQuery.class);
        QueryBuilder.builder(Object.class).alias("o")
                .limit(new Query.Limit(50, 10))
                .build((s, c) -> query);
        verify(query, times(1)).setMaxResults(eq(50));
        verify(query, times(1)).setFirstResult(eq(10));
        verifyNoMoreInteractions(query);
    }

    @Test
    public void usingQuerySort() {
        assertEquals(
                "SELECT o FROM Object o ORDER BY o.col ASC",
                QueryBuilder.builder(Object.class).alias("o")
                        .sort(Optional.of(new Query.Sort("o.col", Query.Sort.Order.ASC)))
                        .buildRawQuery()
        );
    }
}
