package sdk

import (
	"strings"
	"testing"
)

func makeValidWasm(extra int) []byte {
	header := []byte{0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00}
	return append(header, make([]byte, extra)...)
}

func TestValidateBytecode_Valid(t *testing.T) {
	result := ValidateBytecode(makeValidWasm(100))
	if !result.Valid {
		t.Fatalf("expected valid, got errors: %v", result.Errors)
	}
	if len(result.Errors) != 0 {
		t.Fatalf("expected no errors, got %v", result.Errors)
	}
	if result.SizeBytes != 108 {
		t.Fatalf("expected 108 bytes, got %d", result.SizeBytes)
	}
}

func TestValidateBytecode_Empty(t *testing.T) {
	result := ValidateBytecode([]byte{})
	if result.Valid {
		t.Fatal("expected invalid for empty bytes")
	}
	if !strings.Contains(result.Errors[0], "empty") {
		t.Fatalf("expected 'empty' error, got %q", result.Errors[0])
	}
}

func TestValidateBytecode_TooSmall(t *testing.T) {
	result := ValidateBytecode([]byte{0x00, 0x61, 0x73})
	if result.Valid {
		t.Fatal("expected invalid for too-small bytes")
	}
	if !strings.Contains(result.Errors[0], "too small") {
		t.Fatalf("expected 'too small' error, got %q", result.Errors[0])
	}
}

func TestValidateBytecode_TooLarge(t *testing.T) {
	result := ValidateBytecode(makeValidWasm(512 * 1024))
	if result.Valid {
		t.Fatal("expected invalid for too-large bytes")
	}
	found := false
	for _, e := range result.Errors {
		if strings.Contains(e, "too large") {
			found = true
			break
		}
	}
	if !found {
		t.Fatalf("expected 'too large' error, got %v", result.Errors)
	}
}

func TestValidateBytecode_InvalidMagic(t *testing.T) {
	data := []byte{0xFF, 0xFF, 0xFF, 0xFF, 0x01, 0x00, 0x00, 0x00, 0x00}
	result := ValidateBytecode(data)
	if result.Valid {
		t.Fatal("expected invalid for bad magic")
	}
	found := false
	for _, e := range result.Errors {
		if strings.Contains(e, "magic") {
			found = true
			break
		}
	}
	if !found {
		t.Fatalf("expected 'magic' error, got %v", result.Errors)
	}
}

func TestValidateBytecode_InvalidVersion(t *testing.T) {
	data := []byte{0x00, 0x61, 0x73, 0x6D, 0x02, 0x00, 0x00, 0x00, 0x00}
	result := ValidateBytecode(data)
	if result.Valid {
		t.Fatal("expected invalid for bad version")
	}
	found := false
	for _, e := range result.Errors {
		if strings.Contains(e, "version") {
			found = true
			break
		}
	}
	if !found {
		t.Fatalf("expected 'version' error, got %v", result.Errors)
	}
}

func TestEstimateDeployGas_KnownSize(t *testing.T) {
	wasm := makeValidWasm(0) // 8 bytes
	expected := uint64(500_000 + 8*50)
	if got := EstimateDeployGas(wasm); got != expected {
		t.Fatalf("expected %d, got %d", expected, got)
	}
}

func TestEstimateDeployGas_ScalesWithSize(t *testing.T) {
	wasm := makeValidWasm(992) // 1000 bytes
	expected := uint64(500_000 + 1000*50)
	if got := EstimateDeployGas(wasm); got != expected {
		t.Fatalf("expected %d, got %d", expected, got)
	}
}

