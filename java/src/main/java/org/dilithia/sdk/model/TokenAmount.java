package org.dilithia.sdk.model;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Represents a token amount with a fixed number of decimal places.
 *
 * <p>This type carries both the human-readable decimal value and the number of
 * decimals, making it possible to convert losslessly between "display" and
 * "raw" (on-chain integer) representations.</p>
 *
 * <pre>{@code
 * TokenAmount five = TokenAmount.dili("5.0");       // 5 DILI
 * BigInteger raw   = five.toRaw();                  // 5_000_000_000_000_000_000
 *
 * TokenAmount back = TokenAmount.fromRaw(raw, 18);  // round-trip
 * }</pre>
 *
 * @param value    the decimal value (e.g. {@code 1.5} meaning 1.5 tokens)
 * @param decimals the number of decimal places for this token
 */
public record TokenAmount(BigDecimal value, int decimals) {

    /** The number of decimals used by the native DILI token. */
    public static final int DILI_DECIMALS = 18;

    /**
     * Validates that the value is non-null and decimals is non-negative.
     *
     * @throws IllegalArgumentException if {@code value} is null or {@code decimals} is negative
     */
    public TokenAmount {
        if (value == null) throw new IllegalArgumentException("value must not be null");
        if (decimals < 0) throw new IllegalArgumentException("decimals must be >= 0");
    }

    /**
     * Creates a DILI token amount from a decimal string (e.g. {@code "1.5"}).
     *
     * @param value the decimal string
     * @return a new {@code TokenAmount} with {@link #DILI_DECIMALS}
     */
    public static TokenAmount dili(String value) {
        return new TokenAmount(new BigDecimal(value), DILI_DECIMALS);
    }

    /**
     * Creates a DILI token amount from whole tokens.
     *
     * @param wholeTokens the number of whole tokens
     * @return a new {@code TokenAmount} with {@link #DILI_DECIMALS}
     */
    public static TokenAmount dili(long wholeTokens) {
        return new TokenAmount(BigDecimal.valueOf(wholeTokens), DILI_DECIMALS);
    }

    /**
     * Creates a token amount from a raw (on-chain integer) value.
     *
     * @param raw      the raw integer value
     * @param decimals the number of decimal places
     * @return a new {@code TokenAmount}
     */
    public static TokenAmount fromRaw(BigInteger raw, int decimals) {
        return new TokenAmount(new BigDecimal(raw, decimals), decimals);
    }

    /**
     * Creates a token amount from a raw (on-chain integer) value.
     *
     * @param raw      the raw integer value
     * @param decimals the number of decimal places
     * @return a new {@code TokenAmount}
     */
    public static TokenAmount fromRaw(long raw, int decimals) {
        return fromRaw(BigInteger.valueOf(raw), decimals);
    }

    /**
     * Converts this amount to its raw on-chain integer representation.
     *
     * @return the raw value as a {@link BigInteger}
     * @throws ArithmeticException if the conversion is not exact
     */
    public BigInteger toRaw() {
        return value.movePointRight(decimals).toBigIntegerExact();
    }

    /**
     * Returns a human-readable string with trailing zeros stripped.
     *
     * @return the formatted decimal string
     */
    public String formatted() {
        return value.stripTrailingZeros().toPlainString();
    }

    @Override public String toString() {
        return formatted() + " (" + decimals + " decimals)";
    }
}
