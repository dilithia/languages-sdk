package org.dilithia.sdk.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a contract's ABI definition.
 *
 * @param contract the contract name
 * @param methods  the list of method definitions
 */
public record ContractAbi(String contract, List<Map<String, Object>> methods) {}
