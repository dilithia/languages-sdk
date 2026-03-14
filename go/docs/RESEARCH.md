# Go SDK Research

## Priority

Go should follow after TypeScript and Python.

It is strong for:

- services
- infra
- bots
- operators

## Conventions to Follow

- package-oriented API
- explicit `Client` type
- `context.Context` on networked operations
- minimal runtime magic

## Target API Shape

Likely surface:

- `client.New(...)`
- `Client.Balance(ctx, address)`
- `Client.Nonce(ctx, address)`
- `Client.Receipt(ctx, txHash)`
- `Client.AddressSummary(ctx, address)`
- `Client.Simulate(ctx, call)`
- `Client.SendCall(ctx, call, signer)`
- `Client.WaitReceipt(ctx, txHash, opts)`

## Good Practices

- small interfaces
- transport separated from signing
- idiomatic error handling
- no panics in public paths

## CI / Publishing

Planned workflow:

- `go test ./...`
- lint
- module verification
- tagged release flow
