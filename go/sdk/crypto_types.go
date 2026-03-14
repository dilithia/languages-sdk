package sdk

import "errors"

var ErrNativeCryptoUnavailable = errors.New("native crypto adapter is not available")

type NativeCryptoAdapter interface {
	CryptoAdapter
}
