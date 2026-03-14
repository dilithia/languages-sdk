//go:build !cgo

package sdk

func LoadNativeCryptoAdapter() (NativeCryptoAdapter, error) {
	return nil, ErrNativeCryptoUnavailable
}
