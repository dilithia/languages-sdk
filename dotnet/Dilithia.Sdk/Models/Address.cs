namespace Dilithia.Sdk.Models;

/// <summary>
/// Strongly-typed wrapper for a Dilithia account address (e.g. "dili1abc...").
/// </summary>
public readonly record struct Address(string Value)
{
    /// <summary>Create an <see cref="Address"/> from a raw string.</summary>
    public static Address Of(string value) => new(value);

    /// <inheritdoc />
    public override string ToString() => Value;

    /// <summary>Implicit conversion to string.</summary>
    public static implicit operator string(Address a) => a.Value;
}
