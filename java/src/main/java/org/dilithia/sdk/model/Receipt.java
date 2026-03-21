package org.dilithia.sdk.model;

import java.util.Map;

/**
 * Represents a transaction receipt.
 *
 * @param txHash      the transaction hash
 * @param status      the execution status (e.g. "success", "failure")
 * @param blockHeight the block height at which the transaction was included
 * @param gasUsed     the amount of gas consumed
 * @param result      optional result data
 */
public record Receipt(
        TxHash txHash,
        String status,
        long blockHeight,
        long gasUsed,
        Map<String, Object> result
) {

    /**
     * Returns the gas fee paid as a {@link TokenAmount} with the given decimals and gas price.
     *
     * @param gasPrice the gas price per unit
     * @param decimals the token's decimal precision
     * @return the fee as a {@code TokenAmount}
     */
    public TokenAmount feePaidAmount(long gasPrice, int decimals) {
        return TokenAmount.fromRaw(gasUsed * gasPrice, decimals);
    }
}
