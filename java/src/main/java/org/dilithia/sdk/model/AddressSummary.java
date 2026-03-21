package org.dilithia.sdk.model;

/**
 * Represents a summary of an address including balance and nonce.
 *
 * @param address the account address
 * @param balance the account balance in base units
 * @param nonce   the current nonce
 */
public record AddressSummary(Address address, long balance, long nonce) {

    /**
     * Returns the balance as a {@link TokenAmount} with the given number of decimals.
     *
     * @param decimals the token's decimal precision
     * @return the balance as a {@code TokenAmount}
     */
    public TokenAmount balanceAsTokenAmount(int decimals) {
        return TokenAmount.fromRaw(balance, decimals);
    }

    /**
     * Returns the balance as a DILI {@link TokenAmount} (18 decimals).
     *
     * @return the balance as a DILI {@code TokenAmount}
     */
    public TokenAmount balanceAsDili() {
        return balanceAsTokenAmount(TokenAmount.DILI_DECIMALS);
    }
}
