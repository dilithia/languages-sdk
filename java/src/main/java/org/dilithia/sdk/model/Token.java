package org.dilithia.sdk.model;

import java.math.BigDecimal;

/**
 * Describes a token on the Dilithia network including its contract address,
 * display name, ticker symbol, and decimal precision.
 *
 * <p>The static constant {@link #DILI} represents the native network token.</p>
 *
 * <pre>{@code
 * TokenAmount amount = Token.DILI.amount("2.5");
 * }</pre>
 *
 * @param address  the token contract address
 * @param name     the human-readable token name
 * @param symbol   the ticker symbol (e.g. {@code "DILI"})
 * @param decimals the number of decimal places
 */
public record Token(Address address, String name, String symbol, int decimals) {

    /** The native Dilithia network token. */
    public static final Token DILI = new Token(Address.of("token"), "Dilithia", "DILI", 18);

    /**
     * Creates a {@link TokenAmount} for this token from a decimal string.
     *
     * @param value the decimal string (e.g. {@code "1.5"})
     * @return a new {@code TokenAmount} using this token's decimal precision
     */
    public TokenAmount amount(String value) {
        return new TokenAmount(new BigDecimal(value), decimals);
    }

    /**
     * Creates a {@link TokenAmount} for this token from a raw on-chain integer.
     *
     * @param raw the raw integer value
     * @return a new {@code TokenAmount} using this token's decimal precision
     */
    public TokenAmount amountRaw(long raw) {
        return TokenAmount.fromRaw(raw, decimals);
    }
}
