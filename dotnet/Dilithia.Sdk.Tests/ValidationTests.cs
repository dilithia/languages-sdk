using Xunit;
using Dilithia.Sdk.Validation;

namespace Dilithia.Sdk.Tests;

public class ValidationTests
{
    // Valid WASM header: \0asm + version 1
    private static byte[] ValidWasm(int totalSize = 32)
    {
        var bytes = new byte[totalSize];
        bytes[0] = 0x00; bytes[1] = 0x61; bytes[2] = 0x73; bytes[3] = 0x6d;
        bytes[4] = 0x01; bytes[5] = 0x00; bytes[6] = 0x00; bytes[7] = 0x00;
        return bytes;
    }

    [Fact]
    public void Validate_valid_wasm()
    {
        var result = BytecodeValidator.Validate(ValidWasm());
        Assert.True(result.Valid);
        Assert.Empty(result.Errors);
        Assert.Equal(32, result.SizeBytes);
    }

    [Fact]
    public void Validate_empty_bytecode()
    {
        var result = BytecodeValidator.Validate([]);
        Assert.False(result.Valid);
        Assert.Contains("bytecode is empty", result.Errors);
    }

    [Fact]
    public void Validate_too_small()
    {
        var result = BytecodeValidator.Validate(new byte[4]);
        Assert.False(result.Valid);
        Assert.Contains("bytecode too small: must be at least 8 bytes", result.Errors);
    }

    [Fact]
    public void Validate_too_large()
    {
        var bigBytes = ValidWasm(BytecodeValidator.MaxBytecodeSize + 1);
        var result = BytecodeValidator.Validate(bigBytes);
        Assert.False(result.Valid);
        Assert.Contains(result.Errors, e => e.Contains("bytecode too large"));
    }

    [Fact]
    public void Validate_bad_magic_bytes()
    {
        var bytes = ValidWasm();
        bytes[0] = 0xFF; // corrupt magic
        var result = BytecodeValidator.Validate(bytes);
        Assert.False(result.Valid);
        Assert.Contains(result.Errors, e => e.Contains("invalid WASM magic bytes"));
    }

    [Fact]
    public void Validate_bad_version()
    {
        var bytes = ValidWasm();
        bytes[4] = 0x02; // wrong version
        var result = BytecodeValidator.Validate(bytes);
        Assert.False(result.Valid);
        Assert.Contains(result.Errors, e => e.Contains("unsupported WASM version"));
    }

    [Fact]
    public void EstimateDeployGas_formula()
    {
        var bytes = new byte[1000];
        var gas = BytecodeValidator.EstimateDeployGas(bytes);
        Assert.Equal(500_000 + 1000 * 50, gas);
    }

    [Fact]
    public void EstimateDeployGas_empty()
    {
        var gas = BytecodeValidator.EstimateDeployGas([]);
        Assert.Equal(500_000, gas);
    }
}
