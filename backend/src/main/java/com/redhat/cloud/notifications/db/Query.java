package com.redhat.cloud.notifications.db;

import io.sentry.Sentry;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.QueryParam;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class Query {

    // NOTIF-674 Change to "^[a-z0-9_-]+(:(asc|desc))?$" after the IQE test stops using the .
    private static final Pattern SORT_BY_PATTERN = Pattern.compile("^[a-z0-9._-]+(:(asc|desc))?$", CASE_INSENSITIVE);

    private static final int DEFAULT_RESULTS_PER_PAGE  = 20;

    @QueryParam("limit")
    @DefaultValue(DEFAULT_RESULTS_PER_PAGE + "")
    private Integer pageSize;

    @QueryParam("pageNumber")
    private Integer pageNumber;

    @QueryParam("offset")
    private Integer offset;

    @QueryParam("sort_by")
    String sortBy;

    @QueryParam("sortBy")
    @Deprecated
    String sortByDeprecated;

    private String defaultSortBy;
    private Map<String, String> sortFields;

    // Used by test
    public static Query queryWithSortBy(String sortBy) {
        Query query = new Query();
        query.sortBy = sortBy;
        return query;
    }

    public void setSortFields(Map<String, String> sortFields) {
        sortFields.keySet().forEach(key -> {
            if (!key.toLowerCase().equals(key)) {
                throw new IllegalArgumentException("All keys of sort fields must be specified in lower case");
            }
        });
        this.sortFields = Map.copyOf(sortFields);
    }

    public void setDefaultSortBy(String defaultSortBy) {
        this.defaultSortBy = defaultSortBy;
    }

    public static class Limit {
        private final int limit;
        private final int offset;

        public Limit(int limit, int offset) {
            this.limit = limit;
            this.offset = offset;
        }

        public int getLimit() {
            return limit;
        }

        public int getOffset() {
            return offset;
        }

        public static int calculateOffset(int pageNumber, int pageSize) {
            return pageNumber * pageSize;
        }
    }

    public Limit getLimit() {
        if (pageSize == null) {
            pageSize = DEFAULT_RESULTS_PER_PAGE;
        }

        // offset takes precedence over pageNumber if both are set
        if (pageNumber != null && offset == null) {
            offset = Limit.calculateOffset(pageNumber, pageSize);
        }
        if (offset == null) {
            offset = 0;
        }
        return new Limit(pageSize, offset);
    }

    public static class Sort {
        private String sortColumn;

        public enum Order {
            ASC, DESC
        }

        private Order sortOrder;

        public Sort(String sortColumn) {
            this(sortColumn, Order.ASC);
        }

        public Sort(String sortColumn, Order sortOrder) {
            this.sortColumn = sortColumn;
            this.sortOrder = sortOrder;
        }

        public String getSortColumn() {
            return sortColumn;
        }

        public void setSortColumn(String sortColumn) {
            this.sortColumn = sortColumn;
        }

        public Order getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Order sortOrder) {
            this.sortOrder = sortOrder;
        }

        public String getSortQuery() {
            return "ORDER BY " + this.getSortColumn() + " " + this.getSortOrder().toString();
        }
    }

    String getSortBy() {
        if (sortBy != null) {
            return sortBy;
        } else if (sortByDeprecated != null) {
            return sortByDeprecated;
        }

        return defaultSortBy;
    }

    public Optional<Sort> getSort() {
        // Endpoints: sort by: name, type, "last connection status" (?), enabled
        //      -> endpoint_id, name, endpoint_type, enabled are the accepted parameter names
        // TODO Should they be id, name, type, enabled for consistency and then modified in the actual query to Postgres?
        // And if it's not an accepted value? Throw exception?

        String sortBy = getSortBy();

        if (sortBy == null || sortBy.length() < 1) {
            return Optional.empty();
        }

        if (sortFields == null) {
            InternalServerErrorException isee = new InternalServerErrorException("SortFields are not set");
            Sentry.captureException(isee);
            throw isee;
        }

        if (!SORT_BY_PATTERN.matcher(sortBy).matches()) {
            throw new BadRequestException("Invalid 'sort_by' query parameter");
        }

        String[] sortSplit = sortBy.split(":");
        Sort sort = new Sort(sortSplit[0]);
        if (!sortFields.containsKey(sort.sortColumn.toLowerCase())) {
            throw new BadRequestException("Unknown sort field specified: " + sort.sortColumn);
        } else {
            sort.sortColumn = sortFields.get(sort.sortColumn.toLowerCase());
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

    public String getModifiedQuery(String basicQuery) {
        // Use the internal Query
        // What's the proper order? SORT first, then LIMIT? COUNT as last one?
        String query = basicQuery;
        Optional<Sort> sort = getSort();
        if (sort.isPresent()) {
            query = modifyWithSort(query, sort.get());
        }
        return query;
    }

    public static Function<String, String> modifyToCountQuery() {
        return s -> "SELECT COUNT(*) FROM (" +
                s +
                ") counted";
    }

    public static String modifyToCountQuery(String theQuery) {
        return "SELECT COUNT(*) FROM (" +
                theQuery +
                ") counted";
    }

    private static String modifyWithSort(String theQuery, Query.Sort sorter) {
        return theQuery +
                " ORDER BY " +
                sorter.getSortColumn() + " " +
                sorter.getSortOrder().toString();
    }
}
