namespace Dilithia.Sdk.Models;

/// <summary>
/// A name-service record mapping a name to an address.
/// </summary>
/// <param name="Name">The registered name.</param>
/// <param name="Address">The resolved address.</param>
public record NameRecord(string Name, Address Address);
