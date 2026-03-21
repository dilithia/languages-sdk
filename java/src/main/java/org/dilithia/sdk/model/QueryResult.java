package org.dilithia.sdk.model;

import java.util.Map;

/**
 * Represents the result of a read-only contract query.
 *
 * @param result the query result data
 */
public record QueryResult(Map<String, Object> result) {}
