namespace Dilithia.Sdk.Models;

/// <summary>
/// Represents a token quantity with decimal precision.
/// </summary>
public record TokenAmount(decimal Value, int Decimals = 18)
{
    /// <summary>Create a <see cref="TokenAmount"/> from a whole-token string (e.g. "42.5").</summary>
    public static TokenAmount Dili(string value) => new(decimal.Parse(value, System.Globalization.CultureInfo.InvariantCulture));

    /// <summary>Create a <see cref="TokenAmount"/> from whole tokens.</summary>
    public static TokenAmount Dili(long wholeTokens) => new(wholeTokens);

    /// <summary>Create a <see cref="TokenAmount"/> from the smallest indivisible unit.</summary>
    public static TokenAmount FromRaw(long raw, int decimals = 18) =>
        new(raw / (decimal)Math.Pow(10, decimals), decimals);

    /// <summary>Convert to the smallest indivisible unit.</summary>
    public long ToRaw() => (long)(Value * (decimal)Math.Pow(10, Decimals));

    /// <summary>Return a human-readable representation.</summary>
    public string Formatted() => Value.ToString("G", System.Globalization.CultureInfo.InvariantCulture);
}
