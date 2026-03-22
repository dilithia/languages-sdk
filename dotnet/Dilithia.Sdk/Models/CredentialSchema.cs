namespace Dilithia.Sdk.Models;

/// <summary>
/// A credential schema definition registered on-chain.
/// </summary>
/// <param name="Id">The unique schema identifier.</param>
/// <param name="Name">The human-readable schema name.</param>
/// <param name="Issuer">The address of the schema registrant.</param>
/// <param name="Fields">The list of field names defined in the schema.</param>
public record CredentialSchema(string Id, string Name, Address Issuer, List<string> Fields);
