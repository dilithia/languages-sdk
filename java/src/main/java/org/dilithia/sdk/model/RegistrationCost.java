package org.dilithia.sdk.model;

/**
 * Represents the cost to register a name in the name service.
 *
 * @param name     the name being quoted
 * @param cost     the registration cost in base units
 * @param currency the currency identifier (e.g. "DILI")
 */
public record RegistrationCost(String name, long cost, String currency) {

    /**
     * Returns the cost as a {@link TokenAmount} with the given decimals.
     *
     * @param decimals the token's decimal precision
     * @return the cost as a {@code TokenAmount}
     */
    public TokenAmount asTokenAmount(int decimals) {
        return TokenAmount.fromRaw(cost, decimals);
    }

    /**
     * Returns the cost as a DILI {@link TokenAmount} (18 decimals).
     *
     * @return the cost as a DILI {@code TokenAmount}
     */
    public TokenAmount asDili() {
        return asTokenAmount(TokenAmount.DILI_DECIMALS);
    }
}
