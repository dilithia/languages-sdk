using System.Net;
using Xunit;
using Dilithia.Sdk.Models;

namespace Dilithia.Sdk.Tests;

public class CredentialTests
{
    private static DilithiaClient BuildClient(Func<HttpRequestMessage, HttpResponseMessage> handler)
    {
        var mockHandler = new MockHttpMessageHandler(handler);
        return DilithiaClient.Create("http://localhost:9070/rpc")
            .WithHttpClient(new HttpClient(mockHandler))
            .Build();
    }

    // ── Mutations (all go through /call) ────────────────────────────────

    [Fact]
    public async Task RegisterSchemaAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/call", req.RequestUri!.ToString());
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"contract\":\"credential\"", body);
            Assert.Contains("\"method\":\"register_schema\"", body);
            Assert.Contains("\"name\":\"KYC\"", body);
            Assert.Contains("\"fields\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.RegisterSchemaAsync("KYC", new List<string> { "name", "dob", "country" });
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task IssueCredentialAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"issue\"", body);
            Assert.Contains("\"schema_hash\":\"0xschema1\"", body);
            Assert.Contains("\"holder\":\"dili1holder\"", body);
            Assert.Contains("\"claims\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var claims = new Dictionary<string, string> { { "name", "Alice" }, { "country", "CH" } };
        var result = await client.IssueCredentialAsync("0xschema1", "dili1holder", claims);
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task RevokeCredentialAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"revoke\"", body);
            Assert.Contains("\"commitment\":\"0xcred1\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.RevokeCredentialAsync("0xcred1");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    // ── Queries ─────────────────────────────────────────────────────────

    [Fact]
    public async Task VerifyCredentialAsync_returns_true()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/query", req.RequestUri!.ToString());
            Assert.Contains("contract=credential", req.RequestUri.ToString());
            Assert.Contains("method=verify", req.RequestUri.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":true}""")
            };
        });

        var valid = await client.VerifyCredentialAsync("0xcred1");
        Assert.True(valid);
    }

    [Fact]
    public async Task VerifyCredentialAsync_returns_false()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":false}""")
            };
        });

        var valid = await client.VerifyCredentialAsync("0xbadcred");
        Assert.False(valid);
    }

    [Fact]
    public async Task GetCredentialAsync_parses_credential()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("method=get_credential", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":{"id":"0xcred1","schema_hash":"0xschema1","issuer":"dili1issuer","holder":"dili1holder","claims":{"name":"Alice","country":"CH"},"revoked":false}}""")
            };
        });

        var cred = await client.GetCredentialAsync("0xcred1");
        Assert.Equal("0xcred1", cred.Id);
        Assert.Equal("0xschema1", cred.SchemaId);
        Assert.Equal("dili1issuer", cred.Issuer.Value);
        Assert.Equal("dili1holder", cred.Holder.Value);
        Assert.Equal("Alice", cred.Claims["name"]);
        Assert.Equal("CH", cred.Claims["country"]);
        Assert.False(cred.Revoked);
    }

    [Fact]
    public async Task GetCredentialAsync_revoked_credential()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":{"id":"0xcred2","schema_hash":"0xschema1","issuer":"dili1issuer","holder":"dili1holder","claims":{},"revoked":true}}""")
            };
        });

        var cred = await client.GetCredentialAsync("0xcred2");
        Assert.True(cred.Revoked);
    }

    [Fact]
    public async Task GetSchemaAsync_parses_schema()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("method=get_schema", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":{"id":"0xschema1","name":"KYC","issuer":"dili1issuer","fields":["name","dob","country"]}}""")
            };
        });

        var schema = await client.GetSchemaAsync("0xschema1");
        Assert.Equal("0xschema1", schema.Id);
        Assert.Equal("KYC", schema.Name);
        Assert.Equal("dili1issuer", schema.Issuer.Value);
        Assert.Equal(3, schema.Fields.Count);
        Assert.Contains("name", schema.Fields);
        Assert.Contains("dob", schema.Fields);
        Assert.Contains("country", schema.Fields);
    }

    [Fact]
    public async Task ListCredentialsByHolderAsync_parses_list()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("method=list_by_holder", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":[{"id":"0xcred1","schema_hash":"0xschema1","issuer":"dili1issuer","holder":"dili1holder","claims":{"name":"Alice"},"revoked":false},{"id":"0xcred2","schema_hash":"0xschema2","issuer":"dili1issuer2","holder":"dili1holder","claims":{},"revoked":true}]}""")
            };
        });

        var creds = await client.ListCredentialsByHolderAsync("dili1holder");
        Assert.Equal(2, creds.Count);
        Assert.Equal("0xcred1", creds[0].Id);
        Assert.False(creds[0].Revoked);
        Assert.Equal("0xcred2", creds[1].Id);
        Assert.True(creds[1].Revoked);
    }

    [Fact]
    public async Task ListCredentialsByHolderAsync_returns_empty_on_non_array()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":null}""")
            };
        });

        var creds = await client.ListCredentialsByHolderAsync("dili1nobody");
        Assert.Empty(creds);
    }

    [Fact]
    public async Task ListCredentialsByIssuerAsync_parses_list()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("method=list_by_issuer", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":[{"id":"0xcred1","schema_hash":"0xschema1","issuer":"dili1issuer","holder":"dili1holder","claims":{"name":"Alice"},"revoked":false}]}""")
            };
        });

        var creds = await client.ListCredentialsByIssuerAsync("dili1issuer");
        Assert.Single(creds);
        Assert.Equal("0xcred1", creds[0].Id);
        Assert.Equal("dili1issuer", creds[0].Issuer.Value);
    }

    [Fact]
    public async Task ListCredentialsByIssuerAsync_returns_empty_on_non_array()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":null}""")
            };
        });

        var creds = await client.ListCredentialsByIssuerAsync("dili1nobody");
        Assert.Empty(creds);
    }
}
