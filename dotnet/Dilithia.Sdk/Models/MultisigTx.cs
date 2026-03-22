namespace Dilithia.Sdk.Models;

/// <summary>
/// A pending multisig transaction.
/// </summary>
/// <param name="TxId">The unique transaction identifier.</param>
/// <param name="Contract">The target contract for the proposed call.</param>
/// <param name="Method">The target method for the proposed call.</param>
/// <param name="Approvals">The addresses that have approved this transaction.</param>
public record MultisigTx(string TxId, string Contract, string Method, List<string> Approvals);
