using System.Net;
using Xunit;
using Dilithia.Sdk.Models;

namespace Dilithia.Sdk.Tests;

public class NameServiceTests
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
    public async Task RegisterNameAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/call", req.RequestUri!.ToString());
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"contract\":\"name_service\"", body);
            Assert.Contains("\"method\":\"register\"", body);
            Assert.Contains("\"name\":\"alice.dili\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.RegisterNameAsync("alice.dili");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task RenewNameAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"renew\"", body);
            Assert.Contains("\"name\":\"alice.dili\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.RenewNameAsync("alice.dili");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task TransferNameAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"transfer\"", body);
            Assert.Contains("\"name\":\"alice.dili\"", body);
            Assert.Contains("\"new_owner\":\"dili1bob\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.TransferNameAsync("alice.dili", "dili1bob");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task SetNameTargetAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"set_target\"", body);
            Assert.Contains("\"name\":\"alice.dili\"", body);
            Assert.Contains("\"target\":\"dili1new\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.SetNameTargetAsync("alice.dili", "dili1new");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task SetNameRecordAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"set_record\"", body);
            Assert.Contains("\"name\":\"alice.dili\"", body);
            Assert.Contains("\"key\":\"avatar\"", body);
            Assert.Contains("\"value\":\"https://img.png\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.SetNameRecordAsync("alice.dili", "avatar", "https://img.png");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task ReleaseNameAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"release\"", body);
            Assert.Contains("\"name\":\"alice.dili\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.ReleaseNameAsync("alice.dili");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    // ── Queries (all go through /query) ─────────────────────────────────

    [Fact]
    public async Task IsNameAvailableAsync_returns_true()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/query", req.RequestUri!.ToString());
            Assert.Contains("contract=name_service", req.RequestUri.ToString());
            Assert.Contains("method=is_available", req.RequestUri.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":true}""")
            };
        });

        var available = await client.IsNameAvailableAsync("free.dili");
        Assert.True(available);
    }

    [Fact]
    public async Task IsNameAvailableAsync_returns_false()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":false}""")
            };
        });

        var available = await client.IsNameAvailableAsync("taken.dili");
        Assert.False(available);
    }

    [Fact]
    public async Task LookupNameAsync_parses_record()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/query", req.RequestUri!.ToString());
            Assert.Contains("method=lookup", req.RequestUri.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":{"name":"alice.dili","address":"dili1alice"}}""")
            };
        });

        var record = await client.LookupNameAsync("alice.dili");
        Assert.Equal("alice.dili", record.Name);
        Assert.Equal("dili1alice", record.Address.Value);
    }

    [Fact]
    public async Task GetNameRecordsAsync_parses_key_value_pairs()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("method=get_records", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":{"avatar":"https://img.png","email":"alice@example.com"}}""")
            };
        });

        var records = await client.GetNameRecordsAsync("alice.dili");
        Assert.Equal(2, records.Count);
        Assert.Equal("https://img.png", records["avatar"]);
        Assert.Equal("alice@example.com", records["email"]);
    }

    [Fact]
    public async Task GetNameRecordsAsync_returns_empty_on_non_object()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":null}""")
            };
        });

        var records = await client.GetNameRecordsAsync("empty.dili");
        Assert.Empty(records);
    }

    [Fact]
    public async Task GetNamesByOwnerAsync_parses_list()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("method=get_by_owner", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":[{"name":"alice.dili","address":"dili1alice"},{"name":"bob.dili","address":"dili1bob"}]}""")
            };
        });

        var names = await client.GetNamesByOwnerAsync("dili1alice");
        Assert.Equal(2, names.Count);
        Assert.Equal("alice.dili", names[0].Name);
        Assert.Equal("bob.dili", names[1].Name);
    }

    [Fact]
    public async Task GetNamesByOwnerAsync_returns_empty_on_non_array()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":null}""")
            };
        });

        var names = await client.GetNamesByOwnerAsync("dili1nobody");
        Assert.Empty(names);
    }

    [Fact]
    public async Task GetRegistrationCostAsync_parses_cost()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("method=registration_cost", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":{"cost":5000,"duration":31536000}}""")
            };
        });

        var cost = await client.GetRegistrationCostAsync("alice.dili");
        Assert.Equal("alice.dili", cost.Name);
        Assert.Equal(5000, cost.Cost);
        Assert.Equal(31536000, cost.Duration);
    }
}
