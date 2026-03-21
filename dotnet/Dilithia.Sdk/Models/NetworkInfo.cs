namespace Dilithia.Sdk.Models;

/// <summary>
/// Network information returned by the node.
/// </summary>
/// <param name="ChainId">The chain identifier.</param>
/// <param name="BlockHeight">The current block height.</param>
/// <param name="BaseFee">The current base fee.</param>
public record NetworkInfo(string ChainId, long BlockHeight, long BaseFee);
