package com.redhat.cloud.notifications.db;

import io.quarkus.logging.Log;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class Sort {
    private static final Pattern SORT_BY_PATTERN = Pattern.compile("^[a-z0-9_-]+(:(asc|desc))?$", CASE_INSENSITIVE);

    public enum Order {
        ASC, DESC
    }

    public String sortColumn;

    public Order sortOrder;

    private Sort(String sortColumn) {
        this(sortColumn, Order.ASC);
    }

    private Sort(String sortColumn, Order sortOrder) {
        this.sortColumn = sortColumn;
        this.sortOrder = sortOrder;
    }

    public String getSortColumn() {
        return sortColumn;
    }

    public Order getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Order sortOrder) {
        this.sortOrder = sortOrder;
    }

    static void validateSortFields(Map<String, String> sortFields) {
        sortFields.keySet().forEach(key -> {
            if (!key.toLowerCase().equals(key)) {
                throw new IllegalArgumentException("All keys of sort fields must be specified in lower case");
            }
        });
    }


    static String getSortBy(Query restQuery, String defaultSortBy) {
        if (restQuery.sortBy != null) {
            return restQuery.sortBy;
        } else if (restQuery.sortByDeprecated != null) {
            return restQuery.sortByDeprecated;
        }

        return defaultSortBy;
    }

    public static Optional<Sort> getSort(Query restQuery, String defaultSortBy, Map<String, String> sortFields) {
        // Endpoints: sort by: name, type, enabled
        //      -> endpoint_id, name, endpoint_type, enabled are the accepted parameter names
        // And if it's not an accepted value, Throw exception.

        String sortBy = getSortBy(restQuery, defaultSortBy);

        if (sortBy == null || sortBy.length() < 1) {
            return Optional.empty();
        }

        if (sortFields == null) {
            Log.error("Sort fields are not set - this means that an API is using sorting without specifying what sort values are allowed");
            throw new InternalServerErrorException("SortFields are not set");
        }

        validateSortFields(sortFields);

        if (!SORT_BY_PATTERN.matcher(sortBy).matches()) {
            throw new BadRequestException("Invalid 'sort_by' query parameter");
        }

        String[] sortSplit = sortBy.split(":");
        Sort sort = new Sort(sortSplit[0]);
        final String lowerCaseSortColumn = sort.sortColumn.toLowerCase();
        if (!sortFields.containsKey(lowerCaseSortColumn)) {
            throw new BadRequestException("Unknown sort field specified: " + sort.sortColumn);
        } else {
            sort.sortColumn = sortFields.get(lowerCaseSortColumn);
        }

        if (sortSplit.length > 1) {
            try {
                Sort.Order order = Sort.Order.valueOf(sortSplit[1].toUpperCase());
                sort.setSortOrder(order);
            } catch (IllegalArgumentException | NullPointerException iae) {
            }
        }

        return Optional.of(sort);
    }

    public static String getModifiedQuery(String basicQuery, Query restQuery, Map<String, String> sortFields, String defaultSortBy) {
        if (sortFields == null) {
            Log.error("Sort fields are not set - this means that an API is using sorting without specifying what sort values are allowed");
            throw new InternalServerErrorException("SortFields are not set");
        }

        validateSortFields(sortFields);

        // Use the internal Query
        // What's the proper order? SORT first, then LIMIT? COUNT as last one?
        String query = basicQuery;
        Optional<Sort> sort = getSort(restQuery, defaultSortBy, sortFields);
        if (sort.isPresent()) {
            query = modifyWithSort(query, sort.get());
        }
        return query;
    }

    public String getSortQuery() {
        return "ORDER BY " + this.getSortColumn() + " " + this.getSortOrder().toString();
    }

    private static String modifyWithSort(String theQuery, Sort sorter) {
        return theQuery + " " + sorter.getSortQuery();
    }
}
