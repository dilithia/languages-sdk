package org.dilithia.sdk.model;

import java.util.List;

/**
 * Result of validating WASM bytecode.
 *
 * @param valid     whether the bytecode passed all checks
 * @param errors    list of validation error messages (empty when valid)
 * @param sizeBytes size of the input bytecode in bytes
 */
public record BytecodeValidation(boolean valid, List<String> errors, int sizeBytes) {}
