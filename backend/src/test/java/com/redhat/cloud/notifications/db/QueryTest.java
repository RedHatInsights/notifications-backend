package com.redhat.cloud.notifications.db;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class QueryTest {

    @Test
    public void testEmptySort() {
        // Sort is empty if nothing is provided
        Query query = new Query();
        assertTrue(query.getSort().isEmpty());
    }

    @Test
    public void testSortWithoutSortFields() {
        // Throws InternalServerError if sortBy* is provided without sort fields
        Query query = new Query();
        query.sortBy = "foo:desc";
        assertThrows(InternalServerErrorException.class, query::getSort);
    }

    @Test
    public void testUnknownSort() {
        Query query = new Query();
        query.sortBy = "foo:desc";
        query.setSortFields(Map.of("bar", "e.bar"));
        assertThrows(BadRequestException.class, query::getSort);
    }

    @Test
    void testInvalidSortBy() {
        // Throws BadRequest if sortBy* has a wrong syntax
        Query query = new Query();
        query.sortBy = "i am not a valid sortby::";
        query.setSortFields(Map.of("bar", "e.bar"));
        assertThrows(BadRequestException.class, query::getSort);
    }

    @Test
    void testDefaultOrder() {
        // sortBy defaults to order asc if not specified
        Query query = new Query();
        query.sortBy = "bar";
        query.setSortFields(Map.of("bar", "e.bar"));
        Query.Sort sort = query.getSort().get();
        assertEquals("e.bar", sort.getSortColumn());
        assertEquals(Query.Sort.Order.ASC, sort.getSortOrder());
    }

    @Test
    void testInvalidOrder() {
        // throws BadRequest if the order is not valid
        Query query = new Query();
        query.sortBy = "bar:foo";
        query.setSortFields(Map.of("bar", "e.bar"));
        assertThrows(BadRequestException.class, query::getSort);
    }

    @Test
    void testSortByDeprecated() {
        // sortBy (sortByDeprecated) is used if sort_by is not specified
        Query query = new Query();
        query.sortByDeprecated = "foo";
        assertEquals("foo", query.getSortBy());
    }

    @Test
    void preferSnakeCase() {
        // sort_by has higher preference that sortBy query param
        Query query = new Query();
        query.sortBy = "foo";
        query.sortByDeprecated = "bar";
        assertEquals("foo", query.getSortBy());
    }

    @Test
    void testDefaultSortBy() {
        // default sortBy is used if none of the sortBy* were specified
        Query query = new Query();
        query.setDefaultSortBy("foo");
        assertEquals("foo", query.getSortBy());
    }

    @Test
    void testNoDefaultAndNothingProvided() {
        // null if provided if no default or any sortBy* is used
        Query query = new Query();
        assertNull(query.getSortBy());
    }

}
