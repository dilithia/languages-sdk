namespace Dilithia.Sdk.Exceptions;

/// <summary>
/// Thrown when the HTTP response has a non-2xx status code.
/// </summary>
public class HttpException : DilithiaException
{
    /// <summary>The HTTP status code.</summary>
    public int StatusCode { get; }

    /// <summary>The response body, truncated for display.</summary>
    public string Body { get; }

    /// <summary>Create a new <see cref="HttpException"/>.</summary>
    public HttpException(int statusCode, string body)
        : base(body.Length > 0 ? $"HTTP {statusCode}: {body}" : $"HTTP {statusCode}")
    {
        StatusCode = statusCode;
        Body = body;
    }
}
