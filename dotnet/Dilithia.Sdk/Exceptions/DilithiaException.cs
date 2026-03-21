namespace Dilithia.Sdk.Exceptions;

/// <summary>
/// Base exception type for all Dilithia SDK errors.
/// </summary>
public class DilithiaException : Exception
{
    /// <summary>Create a new <see cref="DilithiaException"/>.</summary>
    public DilithiaException(string message) : base(message) { }

    /// <summary>Create a new <see cref="DilithiaException"/> with an inner cause.</summary>
    public DilithiaException(string message, Exception? innerException)
        : base(message, innerException) { }
}
