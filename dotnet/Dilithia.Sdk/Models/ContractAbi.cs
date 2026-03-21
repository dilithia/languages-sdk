namespace Dilithia.Sdk.Models;

/// <summary>
/// The ABI (available methods) for a deployed contract.
/// </summary>
/// <param name="Contract">The contract identifier.</param>
/// <param name="Methods">The list of callable methods.</param>
public record ContractAbi(string Contract, List<AbiMethod> Methods);

/// <summary>
/// A single method in a contract ABI.
/// </summary>
/// <param name="Name">Method name.</param>
/// <param name="Mutates">Whether the method mutates state.</param>
/// <param name="HasArgs">Whether the method accepts arguments.</param>
public record AbiMethod(string Name, bool Mutates, bool HasArgs);
