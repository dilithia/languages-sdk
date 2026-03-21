package org.dilithia.sdk.model;

import java.util.Map;

/**
 * Represents a canonical (deterministically ordered) payload ready for signing.
 *
 * @param fields         the sorted payload fields
 * @param canonicalBytes the canonical JSON bytes for signing
 */
public record CanonicalPayload(Map<String, Object> fields, byte[] canonicalBytes) {}
