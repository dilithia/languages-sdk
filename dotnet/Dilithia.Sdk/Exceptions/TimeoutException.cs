namespace Dilithia.Sdk.Exceptions;

/// <summary>
/// Thrown when an operation exceeds its deadline.
/// </summary>
public class DilithiaTimeoutException : DilithiaException
{
    /// <summary>A description of the operation that timed out.</summary>
    public string Operation { get; }

    /// <summary>Create a new <see cref="DilithiaTimeoutException"/>.</summary>
    public DilithiaTimeoutException(string operation)
        : base($"Timeout: {operation}")
    {
        Operation = operation;
    }
}
