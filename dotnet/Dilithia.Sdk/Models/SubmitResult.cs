namespace Dilithia.Sdk.Models;

/// <summary>
/// The result of submitting a transaction.
/// </summary>
/// <param name="Accepted">Whether the node accepted the transaction.</param>
/// <param name="TxHash">The transaction hash assigned by the node.</param>
public record SubmitResult(bool Accepted, TxHash TxHash);
