# Python SDK Research

## Priority

Python is the second priority after TypeScript.

It is essential for:

- trading bots
- automation
- ops
- data pipelines
- scripting

## Conventions to Follow

- `Client` object plus optional transport abstraction
- sync-first public API with a clean async path only if needed later
- explicit signer objects
- typed models where helpful, but not overengineered

## Target API Shape

Likely surface:

- `DilithiaClient(...)`
- `client.get_balance(address)`
- `client.get_nonce(address)`
- `client.get_receipt(tx_hash)`
- `client.get_address_summary(address)`
- `client.simulate(call)`
- `client.send_call(call, signer)`
- `client.wait_for_receipt(tx_hash, timeout=...)`

## Web3 Design Notes

The Python SDK should feel familiar to users of:

- `web3.py`
- exchange and quant client libraries
- infra automation libraries

The public API should prefer:

- predictable objects
- plain dictionaries only at the RPC edge
- explicit exceptions

## Good Practices

- no magic global state
- clear timeout and retry configuration
- signer abstraction separated from RPC transport
- packaged examples for bots and scripts

## CI / Publishing

Planned workflow:

- install package and test dependencies
- lint
- run unit tests
- build sdist and wheel
- publish to PyPI on tagged releases

## Native Crypto Plan

The Python SDK should not reimplement crypto in Python.

The intended path is:

- RPC client in Python
- native crypto bridge in `python/native/`
- Rust bridge based on `pyo3`
- crypto behavior sourced from `dilithia-core`
