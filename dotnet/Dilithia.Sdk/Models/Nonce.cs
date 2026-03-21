namespace Dilithia.Sdk.Models;

/// <summary>
/// The response from a nonce query.
/// </summary>
/// <param name="Address">The queried address.</param>
/// <param name="NextNonce">The next expected nonce value.</param>
public record Nonce(Address Address, long NextNonce);
