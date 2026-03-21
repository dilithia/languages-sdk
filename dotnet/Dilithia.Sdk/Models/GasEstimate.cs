namespace Dilithia.Sdk.Models;

/// <summary>
/// Gas estimate from the node.
/// </summary>
/// <param name="GasLimit">Maximum gas units for the transaction.</param>
/// <param name="BaseFee">Current base fee per gas unit.</param>
/// <param name="EstimatedCost">Total estimated gas cost.</param>
public record GasEstimate(long GasLimit, long BaseFee, long EstimatedCost);
