//go:build cgo

package sdk

/*
#cgo linux LDFLAGS: -ldl
#include <dlfcn.h>
#include <stdlib.h>

typedef char* (*string_fn)();
typedef char* (*string_arg_fn)(const char*);
typedef char* (*string_u32_fn)(const char*, unsigned int);
typedef char* (*string2_fn)(const char*, const char*);
typedef char* (*string2_u32_fn)(const char*, const char*, unsigned int);
typedef char* (*string3_fn)(const char*, const char*, const char*);
typedef void (*free_fn)(char*);

static void* dilithia_open(const char* path) {
    return dlopen(path, RTLD_NOW | RTLD_LOCAL);
}

static const char* dilithia_error() {
    const char* err = dlerror();
    return err;
}

static void* dilithia_symbol(void* handle, const char* name) {
    return dlsym(handle, name);
}

static char* call_string_fn(void* fn) {
    return ((string_fn)fn)();
}

static char* call_string_arg_fn(void* fn, const char* arg) {
    return ((string_arg_fn)fn)(arg);
}

static char* call_string_u32_fn(void* fn, const char* arg, unsigned int value) {
    return ((string_u32_fn)fn)(arg, value);
}

static char* call_string2_fn(void* fn, const char* arg1, const char* arg2) {
    return ((string2_fn)fn)(arg1, arg2);
}

static char* call_string2_u32_fn(void* fn, const char* arg1, const char* arg2, unsigned int value) {
    return ((string2_u32_fn)fn)(arg1, arg2, value);
}

static char* call_string3_fn(void* fn, const char* arg1, const char* arg2, const char* arg3) {
    return ((string3_fn)fn)(arg1, arg2, arg3);
}

static void call_free_fn(void* fn, char* ptr) {
    ((free_fn)fn)(ptr);
}
*/
import "C"

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"unsafe"
)

type nativeCryptoAdapter struct {
	handle  unsafe.Pointer
	freeFn  unsafe.Pointer
	symbols map[string]unsafe.Pointer
}

type nativeEnvelope struct {
	OK    bool            `json:"ok"`
	Value json.RawMessage `json:"value"`
	Error string          `json:"error"`
}

func LoadNativeCryptoAdapter() (NativeCryptoAdapter, error) {
	path := os.Getenv("DILITHIUM_NATIVE_CORE_LIB")
	if path == "" {
		return nil, ErrNativeCryptoUnavailable
	}
	cpath := C.CString(path)
	defer C.free(unsafe.Pointer(cpath))

	handle := C.dilithia_open(cpath)
	if handle == nil {
		return nil, fmt.Errorf("failed to load native-core: %s", C.GoString(C.dilithia_error()))
	}

	cfreeName := C.CString("dilithia_string_free")
	defer C.free(unsafe.Pointer(cfreeName))
	freeSymbol := C.dilithia_symbol(handle, cfreeName)
	if freeSymbol == nil {
		return nil, errors.New("dilithia_string_free symbol not found")
	}

	symbols := map[string]unsafe.Pointer{}
	for _, name := range []string{
		"dilithia_generate_mnemonic",
		"dilithia_create_wallet_file",
		"dilithia_validate_mnemonic",
		"dilithia_create_hd_wallet_file_from_mnemonic",
		"dilithia_create_hd_wallet_account_from_mnemonic",
		"dilithia_recover_hd_account",
		"dilithia_recover_wallet_file",
		"dilithia_address_from_public_key",
		"dilithia_sign_message",
		"dilithia_verify_message",
	} {
		cname := C.CString(name)
		symbol := C.dilithia_symbol(handle, cname)
		C.free(unsafe.Pointer(cname))
		if symbol == nil {
			return nil, fmt.Errorf("%s symbol not found", name)
		}
		symbols[name] = symbol
	}

	return &nativeCryptoAdapter{
		handle:  handle,
		freeFn:  freeSymbol,
		symbols: symbols,
	}, nil
}

func (a *nativeCryptoAdapter) GenerateMnemonic(ctx context.Context) (string, error) {
	raw, err := a.callNoArg(ctx, "dilithia_generate_mnemonic")
	if err != nil {
		return "", err
	}
	var mnemonic string
	return mnemonic, json.Unmarshal(raw, &mnemonic)
}

func (a *nativeCryptoAdapter) ValidateMnemonic(ctx context.Context, mnemonic string) error {
	_, err := a.callStringArg(ctx, "dilithia_validate_mnemonic", mnemonic)
	return err
}

func (a *nativeCryptoAdapter) RecoverHDWallet(ctx context.Context, mnemonic string) (Account, error) {
	return a.RecoverHDWalletAccount(ctx, mnemonic, 0)
}

func (a *nativeCryptoAdapter) RecoverHDWalletAccount(ctx context.Context, mnemonic string, accountIndex int) (Account, error) {
	raw, err := a.callStringU32(ctx, "dilithia_recover_hd_account", mnemonic, uint32(accountIndex))
	if err != nil {
		return Account{}, err
	}
	var account Account
	return account, json.Unmarshal(raw, &account)
}

func (a *nativeCryptoAdapter) CreateHDWalletFileFromMnemonic(ctx context.Context, mnemonic, password string) (Account, error) {
	raw, err := a.callString2(ctx, "dilithia_create_hd_wallet_file_from_mnemonic", mnemonic, password)
	if err != nil {
		return Account{}, err
	}
	var account Account
	return account, json.Unmarshal(raw, &account)
}

func (a *nativeCryptoAdapter) CreateHDWalletAccountFromMnemonic(ctx context.Context, mnemonic, password string, accountIndex int) (Account, error) {
	raw, err := a.callString2U32(ctx, "dilithia_create_hd_wallet_account_from_mnemonic", mnemonic, password, uint32(accountIndex))
	if err != nil {
		return Account{}, err
	}
	var account Account
	return account, json.Unmarshal(raw, &account)
}

func (a *nativeCryptoAdapter) RecoverWalletFile(ctx context.Context, walletFile WalletFile, mnemonic, password string) (Account, error) {
	payload, err := json.Marshal(walletFile)
	if err != nil {
		return Account{}, err
	}
	raw, err := a.callString3(ctx, "dilithia_recover_wallet_file", string(payload), mnemonic, password)
	if err != nil {
		return Account{}, err
	}
	var account Account
	return account, json.Unmarshal(raw, &account)
}

func (a *nativeCryptoAdapter) AddressFromPublicKey(ctx context.Context, publicKeyHex string) (string, error) {
	raw, err := a.callStringArg(ctx, "dilithia_address_from_public_key", publicKeyHex)
	if err != nil {
		return "", err
	}
	var value struct {
		Address string `json:"address"`
	}
	if err := json.Unmarshal(raw, &value); err != nil {
		return "", err
	}
	return value.Address, nil
}

func (a *nativeCryptoAdapter) SignMessage(ctx context.Context, secretKeyHex, message string) (Signature, error) {
	raw, err := a.callString2(ctx, "dilithia_sign_message", secretKeyHex, message)
	if err != nil {
		return Signature{}, err
	}
	var signature Signature
	return signature, json.Unmarshal(raw, &signature)
}

func (a *nativeCryptoAdapter) VerifyMessage(ctx context.Context, publicKeyHex, message, signatureHex string) (bool, error) {
	raw, err := a.callString3(ctx, "dilithia_verify_message", publicKeyHex, message, signatureHex)
	if err != nil {
		return false, err
	}
	var value struct {
		OK bool `json:"ok"`
	}
	if err := json.Unmarshal(raw, &value); err != nil {
		return false, err
	}
	return value.OK, nil
}

func (a *nativeCryptoAdapter) callNoArg(ctx context.Context, name string) (json.RawMessage, error) {
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
	}
	ptr := C.call_string_fn(a.symbols[name])
	return a.decodeEnvelope(ptr)
}

func (a *nativeCryptoAdapter) callStringArg(ctx context.Context, name string, arg string) (json.RawMessage, error) {
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
	}
	carg := C.CString(arg)
	defer C.free(unsafe.Pointer(carg))
	ptr := C.call_string_arg_fn(a.symbols[name], carg)
	return a.decodeEnvelope(ptr)
}

func (a *nativeCryptoAdapter) callStringU32(ctx context.Context, name, arg string, value uint32) (json.RawMessage, error) {
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
	}
	carg := C.CString(arg)
	defer C.free(unsafe.Pointer(carg))
	ptr := C.call_string_u32_fn(a.symbols[name], carg, C.uint(value))
	return a.decodeEnvelope(ptr)
}

func (a *nativeCryptoAdapter) callString2(ctx context.Context, name, arg1, arg2 string) (json.RawMessage, error) {
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
	}
	carg1 := C.CString(arg1)
	defer C.free(unsafe.Pointer(carg1))
	carg2 := C.CString(arg2)
	defer C.free(unsafe.Pointer(carg2))
	ptr := C.call_string2_fn(a.symbols[name], carg1, carg2)
	return a.decodeEnvelope(ptr)
}

func (a *nativeCryptoAdapter) callString2U32(ctx context.Context, name, arg1, arg2 string, value uint32) (json.RawMessage, error) {
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
	}
	carg1 := C.CString(arg1)
	defer C.free(unsafe.Pointer(carg1))
	carg2 := C.CString(arg2)
	defer C.free(unsafe.Pointer(carg2))
	ptr := C.call_string2_u32_fn(a.symbols[name], carg1, carg2, C.uint(value))
	return a.decodeEnvelope(ptr)
}

func (a *nativeCryptoAdapter) callString3(ctx context.Context, name, arg1, arg2, arg3 string) (json.RawMessage, error) {
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
	}
	carg1 := C.CString(arg1)
	defer C.free(unsafe.Pointer(carg1))
	carg2 := C.CString(arg2)
	defer C.free(unsafe.Pointer(carg2))
	carg3 := C.CString(arg3)
	defer C.free(unsafe.Pointer(carg3))
	ptr := C.call_string3_fn(a.symbols[name], carg1, carg2, carg3)
	return a.decodeEnvelope(ptr)
}

func (a *nativeCryptoAdapter) decodeEnvelope(ptr *C.char) (json.RawMessage, error) {
	if ptr == nil {
		return nil, errors.New("native-core returned null")
	}
	defer C.call_free_fn(a.freeFn, ptr)
	payload := C.GoString(ptr)
	var envelope nativeEnvelope
	if err := json.Unmarshal([]byte(payload), &envelope); err != nil {
		return nil, err
	}
	if !envelope.OK {
		return nil, errors.New(envelope.Error)
	}
	return envelope.Value, nil
}
