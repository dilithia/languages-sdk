package org.dilithia.sdk;

public record Commitment(String hash, long value, String secret, String nonce) {}
