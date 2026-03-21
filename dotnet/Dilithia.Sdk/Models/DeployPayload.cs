namespace Dilithia.Sdk.Models;

/// <summary>
/// Payload for contract deploy/upgrade requests.
/// </summary>
/// <param name="Name">Contract name.</param>
/// <param name="Bytecode">Hex-encoded contract bytecode.</param>
/// <param name="From">Deployer address.</param>
/// <param name="Alg">Signature algorithm identifier.</param>
/// <param name="Pk">Public key (hex).</param>
/// <param name="Sig">Signature (hex).</param>
/// <param name="Nonce">Account nonce.</param>
/// <param name="ChainId">Target chain identifier.</param>
/// <param name="Version">Optional contract version for upgrades.</param>
public record DeployPayload(
    string Name,
    string Bytecode,
    string From,
    string Alg,
    string Pk,
    string Sig,
    long Nonce,
    string ChainId,
    int? Version = null
);
