# Java SDK Research

## Priority

Java should come before Kotlin-specific packaging because a good JVM SDK covers both Java and Kotlin users.

## Conventions to Follow

- Java-first public API
- builder pattern for configuration
- immutable request and response models
- synchronous client first, async extensions later if needed

## Target API Shape

Likely surface:

- `DilithiaClient client = DilithiaClient.builder()...build();`
- `client.getBalance(address)`
- `client.getNonce(address)`
- `client.getReceipt(txHash)`
- `client.getAddressSummary(address)`
- `client.simulate(call)`
- `client.sendCall(call, signer)`
- `client.waitForReceipt(txHash, options)`

## JVM Design Notes

This should be pleasant from both:

- Java
- Kotlin

So the SDK should avoid:

- overly clever generics
- callback-heavy design
- API shapes that depend on Kotlin-specific language features

## CI / Publishing

Planned workflow:

- build
- unit tests
- package publication
- release to Maven Central when ready
