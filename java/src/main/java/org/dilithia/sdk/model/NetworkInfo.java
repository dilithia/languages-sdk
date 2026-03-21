package org.dilithia.sdk.model;

/**
 * Represents network head information.
 *
 * @param blockHeight the current block height
 * @param blockHash   the current block hash
 * @param chainId     the chain identifier
 */
public record NetworkInfo(long blockHeight, String blockHash, String chainId) {}
