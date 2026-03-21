using System.Text.Json;

namespace Dilithia.Sdk.Models;

/// <summary>
/// A transaction receipt.
/// </summary>
public record Receipt(
    TxHash TxHash,
    long BlockHeight,
    string Status,
    JsonElement? Result = null,
    string? Error = null,
    long GasUsed = 0,
    long FeePaid = 0
);
