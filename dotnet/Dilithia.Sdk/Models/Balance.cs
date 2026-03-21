namespace Dilithia.Sdk.Models;

/// <summary>
/// The response from a balance query.
/// </summary>
/// <param name="Address">The queried address.</param>
/// <param name="Value">The parsed balance as a <see cref="TokenAmount"/>.</param>
/// <param name="RawValue">The raw balance string returned by the node.</param>
public record Balance(Address Address, TokenAmount Value, string RawValue);
