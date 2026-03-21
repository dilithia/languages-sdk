# Dilithia .NET SDK

C# client for the Dilithia blockchain -- RPC, contracts, deploy, signing, shielded pool.

## Install

```bash
dotnet add package Dilithia.Sdk
```

## Quick start

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;

// Build a client with the fluent builder
using var client = DilithiaClient.Create("http://localhost:9070/rpc")
    .WithTimeout(TimeSpan.FromSeconds(15))
    .Build();

// Query balance (strongly typed)
var balance = await client.GetBalanceAsync(Address.Of("dili1abc..."));
Console.WriteLine($"Balance: {balance.Value.Formatted()} DILI");

// Get network info
var info = await client.GetNetworkInfoAsync();
Console.WriteLine($"Chain: {info.ChainId}, Height: {info.BlockHeight}");

// Call a contract
var result = await client.CallContractAsync("token", "transfer", new
{
    to = "dili1bob...",
    amount = 1000
});
Console.WriteLine($"Accepted: {result.Accepted}, TxHash: {result.TxHash}");

// Wait for receipt
var receipt = await client.WaitForReceiptAsync(result.TxHash);
Console.WriteLine($"Status: {receipt.Status}, Gas: {receipt.GasUsed}");
```

## Gas sponsor example

```csharp
var result = await client.CallContractAsync(
    contract: "token",
    method: "transfer",
    args: new { to = "dili1bob", amount = 100 },
    paymaster: "dili1sponsor"
);
```

## Bytecode validation

```csharp
using Dilithia.Sdk.Validation;

byte[] wasm = File.ReadAllBytes("contract.wasm");
var validation = BytecodeValidator.Validate(wasm);
if (!validation.Valid)
    Console.WriteLine($"Errors: {string.Join(", ", validation.Errors)}");

long gas = BytecodeValidator.EstimateDeployGas(wasm);
```

## License

MIT OR Apache-2.0
