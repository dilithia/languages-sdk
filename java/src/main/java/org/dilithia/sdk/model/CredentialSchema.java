package org.dilithia.sdk.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a credential schema registered on-chain.
 *
 * @param name       the schema name
 * @param version    the schema version
 * @param attributes the list of attribute definitions (each a map with "name" and "type" keys)
 */
public record CredentialSchema(String name, String version, List<Map<String, String>> attributes) {}
