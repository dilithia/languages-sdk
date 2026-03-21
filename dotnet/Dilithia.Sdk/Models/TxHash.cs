namespace Dilithia.Sdk.Models;

/// <summary>
/// Strongly-typed wrapper for a transaction hash.
/// </summary>
public readonly record struct TxHash(string Value)
{
    /// <summary>Create a <see cref="TxHash"/> from a raw string.</summary>
    public static TxHash Of(string value) => new(value);

    /// <inheritdoc />
    public override string ToString() => Value;

    /// <summary>Implicit conversion to string.</summary>
    public static implicit operator string(TxHash h) => h.Value;
}
