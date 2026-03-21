package sdk

import "fmt"

// WASM magic bytes: \0asm
var wasmMagic = []byte{0x00, 0x61, 0x73, 0x6D}

// WASM version 1
var wasmVersion1 = []byte{0x01, 0x00, 0x00, 0x00}

const (
	// MaxBytecodeSize is the maximum allowed bytecode size (512 KB).
	MaxBytecodeSize = 512 * 1024

	// BaseDeployGas is the base gas cost for deploying a contract.
	BaseDeployGas uint64 = 500_000

	// PerByteGas is the gas cost per byte of bytecode.
	PerByteGas uint64 = 50
)

// BytecodeValidation holds the result of validating WASM bytecode.
type BytecodeValidation struct {
	Valid     bool     `json:"valid"`
	Errors    []string `json:"errors"`
	SizeBytes int      `json:"size_bytes"`
}

// ValidateBytecode validates raw WASM bytecode.
//
// Checks magic bytes, version header, and size constraints.
// This is a lightweight client-side check — no WASM parsing or RPC required.
func ValidateBytecode(wasmBytes []byte) BytecodeValidation {
	var errors []string
	sizeBytes := len(wasmBytes)

	if sizeBytes == 0 {
		errors = append(errors, "bytecode is empty")
		return BytecodeValidation{Valid: false, Errors: errors, SizeBytes: sizeBytes}
	}

	if sizeBytes < 8 {
		errors = append(errors, "bytecode too small: must be at least 8 bytes")
		return BytecodeValidation{Valid: false, Errors: errors, SizeBytes: sizeBytes}
	}

	if sizeBytes > MaxBytecodeSize {
		errors = append(errors, fmt.Sprintf(
			"bytecode too large: %d bytes exceeds maximum of %d bytes",
			sizeBytes, MaxBytecodeSize,
		))
	}

	magicOk := true
	for i := 0; i < 4; i++ {
		if wasmBytes[i] != wasmMagic[i] {
			magicOk = false
			break
		}
	}
	if !magicOk {
		errors = append(errors, "invalid WASM magic bytes: expected \\0asm")
	}

	versionOk := true
	for i := 0; i < 4; i++ {
		if wasmBytes[4+i] != wasmVersion1[i] {
			versionOk = false
			break
		}
	}
	if !versionOk {
		errors = append(errors, "unsupported WASM version: expected version 1")
	}

	return BytecodeValidation{
		Valid:     len(errors) == 0,
		Errors:    errors,
		SizeBytes: sizeBytes,
	}
}

// EstimateDeployGas estimates the gas cost for deploying WASM bytecode.
//
// Uses a simple heuristic: BaseDeployGas + len(wasmBytes) * PerByteGas.
func EstimateDeployGas(wasmBytes []byte) uint64 {
	return BaseDeployGas + uint64(len(wasmBytes))*PerByteGas
}
