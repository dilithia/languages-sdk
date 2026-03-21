package org.dilithia.sdk.model;

/**
 * Represents an account balance.
 *
 * @param address the account address
 * @param balance the balance value in base units
 */
public record Balance(Address address, long balance) {

    /**
     * Returns the balance as a {@link TokenAmount} with the given number of decimals.
     *
     * @param decimals the token's decimal precision
     * @return the balance as a {@code TokenAmount}
     */
    public TokenAmount asTokenAmount(int decimals) {
        return TokenAmount.fromRaw(balance, decimals);
    }

    /**
     * Returns the balance as a DILI {@link TokenAmount} (18 decimals).
     *
     * @return the balance as a DILI {@code TokenAmount}
     */
    public TokenAmount asDili() {
        return asTokenAmount(TokenAmount.DILI_DECIMALS);
    }
}
