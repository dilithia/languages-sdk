namespace Dilithia.Sdk;

/// <summary>
/// Fluent builder for constructing a <see cref="DilithiaClient"/>.
/// </summary>
public sealed class DilithiaClientBuilder
{
    private readonly string _rpcUrl;
    private HttpClient? _httpClient;
    private TimeSpan _timeout = TimeSpan.FromSeconds(10);
    private string? _chainBaseUrl;
    private string? _jwt;
    private readonly Dictionary<string, string> _headers = new();

    /// <summary>
    /// Create a builder targeting the given RPC URL.
    /// </summary>
    public DilithiaClientBuilder(string rpcUrl)
    {
        _rpcUrl = rpcUrl ?? throw new ArgumentNullException(nameof(rpcUrl));
    }

    /// <summary>Set a custom <see cref="HttpClient"/> (useful for testing with mock handlers).</summary>
    public DilithiaClientBuilder WithHttpClient(HttpClient httpClient)
    {
        _httpClient = httpClient;
        return this;
    }

    /// <summary>Set the request timeout.</summary>
    public DilithiaClientBuilder WithTimeout(TimeSpan timeout)
    {
        _timeout = timeout;
        return this;
    }

    /// <summary>Set the chain base URL for REST endpoints (derived from rpcUrl if omitted).</summary>
    public DilithiaClientBuilder WithChainBaseUrl(string baseUrl)
    {
        _chainBaseUrl = baseUrl;
        return this;
    }

    /// <summary>Set a JWT bearer token for authenticated endpoints.</summary>
    public DilithiaClientBuilder WithJwt(string jwt)
    {
        _jwt = jwt;
        return this;
    }

    /// <summary>Add a custom HTTP header sent with every request.</summary>
    public DilithiaClientBuilder WithHeader(string name, string value)
    {
        _headers[name] = value;
        return this;
    }

    /// <summary>Build and return a configured <see cref="DilithiaClient"/>.</summary>
    public DilithiaClient Build()
    {
        return new DilithiaClient(
            rpcUrl: _rpcUrl,
            httpClient: _httpClient,
            timeout: _timeout,
            chainBaseUrl: _chainBaseUrl,
            jwt: _jwt,
            headers: _headers.Count > 0 ? new Dictionary<string, string>(_headers) : null
        );
    }
}
