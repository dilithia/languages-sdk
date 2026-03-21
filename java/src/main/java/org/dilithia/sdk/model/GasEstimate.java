package org.dilithia.sdk.model;

/**
 * Represents a gas estimate for a transaction.
 *
 * @param gasLimit the estimated gas limit
 * @param gasPrice the estimated gas price
 */
public record GasEstimate(long gasLimit, long gasPrice) {}
