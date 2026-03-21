namespace Dilithia.Sdk.Validation;

/// <summary>
/// Result of validating WASM bytecode.
/// </summary>
public record BytecodeValidation(bool Valid, List<string> Errors, int SizeBytes);

/// <summary>
/// Client-side WASM bytecode validation utilities.
/// </summary>
public static class BytecodeValidator
{
    /// <summary>WASM magic bytes: \0asm</summary>
    private static readonly byte[] WasmMagic = [0x00, 0x61, 0x73, 0x6d];

    /// <summary>WASM version 1 bytes.</summary>
    private static readonly byte[] WasmVersion1 = [0x01, 0x00, 0x00, 0x00];

    /// <summary>Maximum bytecode size in bytes (512 KB).</summary>
    public const int MaxBytecodeSize = 512 * 1024;

    /// <summary>Base gas cost for deploying a contract.</summary>
    public const long BaseDeployGas = 500_000;

    /// <summary>Gas cost per byte of bytecode.</summary>
    public const long PerByteGas = 50;

    /// <summary>
    /// Validate raw WASM bytecode.
    /// Checks magic bytes, version header, and size constraints.
    /// </summary>
    /// <param name="wasmBytes">Raw WASM binary data.</param>
    /// <returns>A <see cref="BytecodeValidation"/> result.</returns>
    public static BytecodeValidation Validate(byte[] wasmBytes)
    {
        var errors = new List<string>();
        var sizeBytes = wasmBytes.Length;

        if (sizeBytes == 0)
        {
            errors.Add("bytecode is empty");
            return new BytecodeValidation(false, errors, sizeBytes);
        }

        if (sizeBytes < 8)
        {
            errors.Add("bytecode too small: must be at least 8 bytes");
            return new BytecodeValidation(false, errors, sizeBytes);
        }

        if (sizeBytes > MaxBytecodeSize)
        {
            errors.Add($"bytecode too large: {sizeBytes} bytes exceeds maximum of {MaxBytecodeSize} bytes");
        }

        bool magicOk = wasmBytes[0] == WasmMagic[0]
                     && wasmBytes[1] == WasmMagic[1]
                     && wasmBytes[2] == WasmMagic[2]
                     && wasmBytes[3] == WasmMagic[3];
        if (!magicOk)
        {
            errors.Add("invalid WASM magic bytes: expected \\0asm");
        }

        bool versionOk = wasmBytes[4] == WasmVersion1[0]
                       && wasmBytes[5] == WasmVersion1[1]
                       && wasmBytes[6] == WasmVersion1[2]
                       && wasmBytes[7] == WasmVersion1[3];
        if (!versionOk)
        {
            errors.Add("unsupported WASM version: expected version 1");
        }

        return new BytecodeValidation(errors.Count == 0, errors, sizeBytes);
    }

    /// <summary>
    /// Estimate the gas cost for deploying WASM bytecode.
    /// Uses a simple heuristic: <c>BaseDeployGas + len(wasmBytes) * PerByteGas</c>.
    /// </summary>
    /// <param name="wasmBytes">Raw WASM binary data.</param>
    /// <returns>Estimated gas cost.</returns>
    public static long EstimateDeployGas(byte[] wasmBytes) =>
        BaseDeployGas + (long)wasmBytes.Length * PerByteGas;
}
