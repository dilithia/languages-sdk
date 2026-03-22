namespace Dilithia.Sdk.Models;

/// <summary>
/// A verifiable credential issued on-chain.
/// </summary>
/// <param name="Id">The unique credential identifier.</param>
/// <param name="SchemaId">The schema this credential conforms to.</param>
/// <param name="Issuer">The address of the credential issuer.</param>
/// <param name="Holder">The address of the credential holder.</param>
/// <param name="Claims">The credential claims as key-value pairs.</param>
/// <param name="Revoked">Whether the credential has been revoked.</param>
public record Credential(string Id, string SchemaId, Address Issuer, Address Holder, Dictionary<string, string> Claims, bool Revoked);
