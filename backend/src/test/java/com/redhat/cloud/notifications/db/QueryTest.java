package com.redhat.cloud.notifications.db;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.ws.rs.BadRequestException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class QueryTest {

    @Test
    public void testSort() {

        // Sort is empty if nothing is provided
        Query query = new Query();
        assertTrue(query.getSort().isEmpty());

        // Throws BadRequest if sortBy* is provided without sort fields
        query = new Query();
        query.sortBy = "foo:desc";
        assertThrows(BadRequestException.class, query::getSort);

        // Throws BadRequest if sortBy* is not found in the sort fields
        query = new Query();
        query.sortBy = "foo:desc";
        query.setSortFields(Map.of("bar", "e.bar"));
        assertThrows(BadRequestException.class, query::getSort);

        // Throws BadRequest if sortBy* has a wrong syntax
        query = new Query();
        query.sortBy = "i am not a valid sortby::";
        query.setSortFields(Map.of("bar", "e.bar"));
        assertThrows(BadRequestException.class, query::getSort);

        // sortBy defaults to order asc if not specified
        query = new Query();
        query.sortBy = "bar";
        query.setSortFields(Map.of("bar", "e.bar"));
        Query.Sort sort = query.getSort().get();
        assertEquals("e.bar", sort.getSortColumn());
        assertEquals(Query.Sort.Order.ASC, sort.getSortOrder());

        // throws BadRequest if the order is not valid
        query = new Query();
        query.sortBy = "bar:foo";
        query.setSortFields(Map.of("bar", "e.bar"));
        assertThrows(BadRequestException.class, query::getSort);

        // sortBy (sortByDeprecated) is used if sort_by is not specified
        query = new Query();
        query.sortByDeprecated = "foo";
        assertEquals("foo", query.getSortBy());

        // sort_by has higher preference that sortBy query param
        query = new Query();
        query.sortBy = "foo";
        query.sortByDeprecated = "bar";
        assertEquals("foo", query.getSortBy());

        // default sortBy is used if none of the sortBy* were specified
        query = new Query();
        query.setDefaultSortBy("foo");
        assertEquals("foo", query.getSortBy());

        // null if provided if no default or any sortBy* is used
        query = new Query();
        assertNull(query.getSortBy());
    }

}
