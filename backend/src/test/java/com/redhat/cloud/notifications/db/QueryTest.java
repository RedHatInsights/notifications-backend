package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class QueryTest {

    @Inject
    Validator validator;
    @InjectMock
    EndpointRepository endpointRepository;

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
     * {@link Query#pageSize} fields contain a valid value.
     */
    @Test
    public void validMinimumFieldsTest() {
        final Query query = new Query();

        // Set a valid value for the collection parameters.
        query.offset = 50;
        query.pageNumber = 50;
        query.pageSize = 50;

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
     * Tests that the resources or handlers return a proper error message when the collection limits, page number and
     * offset have invalid values. The maximum and minimum values to test are taken directly from the {@link Query} class
     * annotations by using reflection.
     * @throws NoSuchFieldException if the fields from the {@link Query} class cannot be accessed.
     */
    @Test
    public void queryTestResources() throws NoSuchFieldException {
        // Get the annotation's values from the "pageSize" field of the "Query" class.
        final long maxQueryPageSizeValue = this.getMaxValueFromAnnotation("pageSize");
        final long minQueryPageSizeValue = this.getMinValueFromAnnotation("pageSize");

        // Get the annotation's values from the "pageNumber" field of the "Query" class.
        final long minQueryPageNumberValue = this.getMinValueFromAnnotation("pageNumber");

        // Get the annotation's values from the "offset" field of the "Query" class.
        final long minQueryOffsetValue = this.getMinValueFromAnnotation("offset");

        // Set up the RBAC access for the test.
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        // Declare the test values and the expected error messages we should be getting.
        final Map<Long, String> limitValuesErrorMessages = Map.of(
            maxQueryPageSizeValue + 1, String.format("The collection limit cannot be greater than %s", maxQueryPageSizeValue),
            minQueryPageSizeValue - 1, String.format("The collection limit cannot be lower than %s", minQueryPageSizeValue)
        );

        final Map<Long, String> pageNumberValuesErrorMessages = Map.of(
            minQueryPageNumberValue - 1, String.format("The page number cannot be lower than %s", minQueryPageNumberValue)
        );

        final Map<Long, String> offsetValuesErrorMessages = Map.of(
            minQueryOffsetValue - 1, String.format("The offset cannot be lower than %s", minQueryOffsetValue)
        );

        // Link the query parameters with the test cases we want to run.
        final Map<String, Map<Long, String>> collectionLimitParameters = Map.of(
            "limit", limitValuesErrorMessages,
            "pageNumber", pageNumberValuesErrorMessages,
            "offset", offsetValuesErrorMessages
        );

        // Create an endpoint that will be used in one of the URLs below.
        final UUID endpointId = UUID.randomUUID();
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(true);

        // Declare the URLs that will be tested.
        final List<String> urls = new ArrayList<>();
        urls.add("/api/integrations/v1.0/endpoints");
        urls.add(String.format("/api/integrations/v1.0/endpoints/%s/history", endpointId));
        urls.add("/api/notifications/v1.0/notifications/events");
        urls.add("/api/notifications/v1.0/notifications/eventTypes");
        urls.add("/api/notifications/v1.0/notifications/eventTypes/467431eb-5fd5-49f8-a47a-b35165f9cc3f/behaviorGroups");

        // For each url...
        for (final var url : urls) {
            // ... get the query parameter groups...
            for (final var queryParamGroup : collectionLimitParameters.entrySet()) {
                // ... and for each group get the test value and the expected error message.
                for (final var queryParam : queryParamGroup.getValue().entrySet()) {
                    final String response = given()
                        .header(identityHeader)
                        .when()
                        .contentType(ContentType.JSON)
                        .queryParam(queryParamGroup.getKey(), queryParam.getKey())
                        .get(url)
                        .then()
                        .statusCode(HttpStatus.SC_BAD_REQUEST)
                        .extract()
                        .asString();

                    final String constraintViolation = TestHelpers.extractConstraintViolationFromResponse(response);

                    Assertions.assertEquals(
                        queryParam.getValue(),
                        constraintViolation,
                        String.format(
                            "unexpected error message received for query parameter \"%s\" with value \"%s\" for url \"%s\"",
                            queryParamGroup.getKey(),
                            queryParam.getKey(),
                            url
                        )
                    );
                }
            }
        }
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
