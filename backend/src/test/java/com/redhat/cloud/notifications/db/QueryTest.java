package com.redhat.cloud.notifications.db;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class QueryTest {

    private Validator validator;

    @BeforeEach
    protected void setUp() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            this.validator = validatorFactory.getValidator();
        }
    }

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

    /**
     * Tests that no constraint is violated when the {@link Query#offset}, {@link Query#pageNumber} and
     * {@link Query#pageSize} fields contain a valid value. Reflection is used to grab the values from the annotations
     * and to generate a valid value that shouldn't trigger any errors.
     * @throws NoSuchFieldException if the field doesn't exist in the object.
     */
    @Test
    public void validMinimumFieldsTest() throws NoSuchFieldException {
        // Get the annotation's values from the "offset" field of the "Query" class.
        final long minQueryOffsetValue = this.getMinValueFromAnnotation("offset");

        // Get the annotation's values from the "pageNumber" field of the "Query" class.
        final long minQueryPageNumberValue = this.getMinValueFromAnnotation("pageNumber");

        // Get the annotation's values from the "pageSize" field of the "Query" class.
        final long maxQueryPageSizeValue = this.getMaxValueFromAnnotation("pageSize");
        final long minQueryPageSizeValue = this.getMinValueFromAnnotation("pageSize");

        // Get a random number that will be used for the validation. The number must be between the "min" and "max"
        // values of these annotations, so make sure we generate one that satisfies that constraint.
        final int minRand = (int) Math.min(minQueryOffsetValue, Math.min(minQueryPageNumberValue, minQueryPageSizeValue));
        final int validValue = ThreadLocalRandom.current().nextInt(minRand, (int) maxQueryPageSizeValue);

        final Query query = new Query();

        query.offset = validValue;
        query.pageNumber = validValue;
        query.pageSize = validValue;

        // Test if the validation works as intended.
        final var constraintViolations = this.validator.validate(query);

        final var expectedNumberConstraintViolations = 0;
        Assertions.assertEquals(expectedNumberConstraintViolations, constraintViolations.size(), "unexpected number of constraint violations");
    }

    /**
     * Tests that a constraint is violated when the {@link Query#offset} field of a {@link Query} object contains an
     * invalid value. Reflection is used to get the minimum allowed value from the annotation so that a proper invalid
     * value is generated for the test.
     * @throws NoSuchFieldException if the field doesn't exist in the object.
     */
    @Test
    public void minimumOffsetTest() throws NoSuchFieldException {
        final Query query = new Query();

        // Get the annotation's values from the "offset" field of the "Query" class.
        final int minQueryOffsetValue = (int) this.getMinValueFromAnnotation("offset");

        // Set the field to an invalid value.
        query.offset = minQueryOffsetValue - 1;

        // Test that the returned error template from the validation match the one expected.
        this.testValidationTemplate(query, "The offset cannot be lower than {value}");
    }

    /**
     * Tests that a constraint is violated when the {@link Query#pageNumber}" field of a {@link Query} object contains
     * an invalid value. Reflection is used to get the minimum allowed value from the annotation so that a proper invalid
     * value is generated for the test.
     * @throws NoSuchFieldException if the field doesn't exist in the object.
     */
    @Test
    public void minimumPageNumberTest() throws NoSuchFieldException {
        final Query query = new Query();

        // Get the annotation's values from the "pageNumber" field of the "Query" class.
        final int minQueryPageSizeValue = (int) this.getMinValueFromAnnotation("pageNumber");

        // Set the field to an invalid value.
        query.pageNumber = minQueryPageSizeValue - 1;

        // Test that the returned error template from the validation match the one expected.
        this.testValidationTemplate(query, "The page number cannot be lower than {value}");
    }

    /**
     * Tests that a constraint is violated when the {@link Query#pageSize}" field of a {@link Query} object contains
     * a value that is greater than the maximum. Reflection is used to get the minimum allowed value from the
     * annotation so that a proper invalid value is generated for the test.
     * @throws NoSuchFieldException if the field doesn't exist in the object.
     */
    @Test
    public void maximumPageSizeTest() throws NoSuchFieldException {
        final Query query = new Query();

        // Get the annotation's values from the "pageSize" field of the "Query" class.
        final int maxQueryPageSizeValue = (int) this.getMaxValueFromAnnotation("pageSize");

        // Set the field to an invalid value.
        query.pageSize = maxQueryPageSizeValue + 1;

        // Test that the returned error template from the validation match the one expected.
        this.testValidationTemplate(query, "The collection limit cannot be greater than {value}");
    }

    /**
     * Tests that a constraint is violated when the {@link Query#pageSize}" field of a {@link Query} object contains
     * an invalid value. Reflection is used to get the minimum allowed value from the annotation so that a proper invalid
     * value is generated for the test.
     * @throws NoSuchFieldException if the field doesn't exist in the object.
     */
    @Test
    public void minimumPageSizeTest() throws NoSuchFieldException {
        final Query query = new Query();

        // Get the annotation's values from the "pageSize" field of the "Query" class.
        final int minQueryPageSizeValue = (int) this.getMinValueFromAnnotation("pageSize");

        // Set the field accessible so that it can be modified.
        query.pageSize = minQueryPageSizeValue - 1;

        // Test that the returned error template from the validation match the one expected.
        this.testValidationTemplate(query, "The collection limit cannot be lower than {value}");
    }

    /**
     * Gets the value from the {@link Min} annotation from the {@link Query} class.
     * @param field the field to get the annotation's value from.
     * @return the value of the annotation.
     */
    private long getMinValueFromAnnotation(final String field) throws NoSuchFieldException {
        final Field classField = Query.class.getDeclaredField(field);
        final Min minClassAnnotation = classField.getAnnotation(Min.class);

        return minClassAnnotation.value();
    }

    /**
     * Gets the value from the {@link Max} annotation from the {@link Query} class.
     * @param field the field to get the annotation's value from.
     * @return the value of the annotation.
     */
    private long getMaxValueFromAnnotation(final String field) throws NoSuchFieldException {
        final Field classField = Query.class.getDeclaredField(field);
        final Max maxClassAnnotation = classField.getAnnotation(Max.class);

        return maxClassAnnotation.value();
    }

    /**
     * Tests that after validating the given {@link Query} object, the validator returns the expected error template.
     * @param query the query to validate.
     * @param expectedErrorTemplate the expected error template to compare from.
     */
    private void testValidationTemplate(final Query query, final String expectedErrorTemplate) {
        final var constraintViolations = this.validator.validate(query);

        final var expectedNumberConstraintViolations = 1;
        Assertions.assertEquals(expectedNumberConstraintViolations, constraintViolations.size(), "unexpected number of constraint violations");

        for (final var cv : constraintViolations) {
            Assertions.assertEquals(expectedErrorTemplate, cv.getMessageTemplate(), "unexpected message template for the constraint violation");
        }
    }
}
