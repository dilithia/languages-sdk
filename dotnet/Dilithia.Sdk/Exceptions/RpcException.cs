namespace Dilithia.Sdk.Exceptions;

/// <summary>
/// Thrown when the JSON-RPC endpoint returns an error object.
/// </summary>
public class RpcException : DilithiaException
{
    /// <summary>The JSON-RPC error code.</summary>
    public int Code { get; }

    /// <summary>The human-readable error message from the node.</summary>
    public string RpcMessage { get; }

    /// <summary>Create a new <see cref="RpcException"/>.</summary>
    public RpcException(int code, string rpcMessage)
        : base($"RPC error {code}: {rpcMessage}")
    {
        Code = code;
        RpcMessage = rpcMessage;
    }
}
